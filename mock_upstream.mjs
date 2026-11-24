// 本地模拟官方房间服务：验证中继的 HTTP 反代与 WS 隧道（含 Origin 注入与 accept 计算）
import http from 'node:http';
import crypto from 'node:crypto';
const GUID = '258EAFA5-E914-47DA-95CA-C5AB0DC85B11';
const HOST = '127.0.0.1', PORT = 9443;

const srv = http.createServer((req, res) => {
  const origin = req.headers['origin'] || '';
  if (req.url.startsWith('/api/')) {
    if (origin !== 'https://www.bilibili.com') {
      res.writeHead(403, { 'content-type': 'application/json' });
      return res.end(JSON.stringify({ error: '不允许从这个网页访问' }));
    }
    res.writeHead(200, { 'content-type': 'application/json' });
    return res.end(JSON.stringify({ mock: true, path: req.url, origin }));
  }
  res.writeHead(404); res.end('nope');
});

srv.on('upgrade', (req, sock) => {
  const origin = req.headers['origin'] || '';
  const key = req.headers['sec-websocket-key'] || '';
  if (req.url !== '/p2p' || origin !== 'https://www.bilibili.com' || !key) {
    sock.end('HTTP/1.1 403 Forbidden\r\n\r\n');
    return;
  }
  const accept = crypto.createHash('sha1').update(key + GUID).digest('base64');
  sock.write(`HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ${accept}\r\n\r\n`);
  // 服务端帧（不掩码）
  const send = (obj) => {
    const p = Buffer.from(JSON.stringify(obj), 'utf8');
    const head = p.length < 126 ? Buffer.from([0x81, p.length])
      : Buffer.from([0x81, 126, p.length >> 8, p.length & 255]);
    sock.write(Buffer.concat([head, p]));
  };
  let buf = Buffer.alloc(0);
  sock.on('data', (d) => {
    buf = Buffer.concat([buf, d]);
    // 解析一个客户端帧（掩码）
    while (buf.length >= 2) {
      const op = buf[0] & 0x0f, len0 = buf[1] & 0x7f;
      let off = 2, len = len0;
      if (len0 === 126) { if (buf.length < 4) return; len = buf.readUInt16BE(2); off = 4; }
      const mask = buf.subarray(off, off + 4);
      if (buf.length < off + 4 + len) return;
      const payload = Buffer.from(buf.subarray(off + 4, off + 4 + len));
      for (let i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
      buf = buf.subarray(off + 4 + len);
      if (op === 1) {
        let m = {}; try { m = JSON.parse(payload.toString('utf8')); } catch (e) {}
        if (m.t === 'host') send({ t: 'hosted', code: m.code, ice: { iceServers: [], relayOnly: true }, max: 4, mock: true });
        else send({ t: 'echo', got: m });
      }
    }
  });
});
srv.listen(PORT, HOST, () => console.log('mock upstream on ' + HOST + ':' + PORT));
