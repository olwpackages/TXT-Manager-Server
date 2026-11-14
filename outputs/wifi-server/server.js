"use strict";

const https = require("node:https");
const fs = require("node:fs");
const path = require("node:path");

const here = __dirname;
const outputDir = path.join(here, "received");
const token = process.env.WIFI_UPLOAD_TOKEN;
if (!token || !process.env.WIFI_SERVER_CERT || !process.env.WIFI_SERVER_KEY) {
  console.error("Set WIFI_UPLOAD_TOKEN, WIFI_SERVER_CERT, and WIFI_SERVER_KEY before starting the server.");
  process.exit(1);
}
const maxBytes = 10_000_000;

function safeName(value) {
  let name;
  try {
    name = decodeURIComponent(value || "Заметка.txt");
  } catch {
    return null;
  }
  name = path.basename(name.replaceAll("\\", "/"));
  if (!name.toLowerCase().endsWith(".txt") || name.length > 120 || name === ".txt") return null;
  return name;
}

function uniquePath(name) {
  const ext = path.extname(name);
  const stem = path.basename(name, ext);
  let target = path.join(outputDir, name);
  for (let i = 1; fs.existsSync(target); i++) target = path.join(outputDir, `${stem} (${i})${ext}`);
  return target;
}

const server = https.createServer({
  cert: fs.readFileSync(process.env.WIFI_SERVER_CERT),
  key: fs.readFileSync(process.env.WIFI_SERVER_KEY),
}, (req, res) => {
  if (req.method !== "POST" || req.url !== "/upload" || req.headers.authorization !== `Bearer ${token}`) {
    res.writeHead(403).end("forbidden");
    return;
  }

  const name = safeName(req.headers["x-file-name"]);
  if (!name) {
    res.writeHead(400).end("invalid filename");
    return;
  }

  let size = 0;
  const chunks = [];
  req.on("data", (chunk) => {
    size += chunk.length;
    if (size > maxBytes) {
      res.writeHead(413).end("file too large");
      req.destroy();
      return;
    }
    chunks.push(chunk);
  });
  req.on("end", () => {
    if (res.writableEnded) return;
    fs.mkdirSync(outputDir, { recursive: true });
    const target = uniquePath(name);
    fs.writeFile(target, Buffer.concat(chunks), { flag: "wx" }, (error) => {
      if (error) {
        res.writeHead(500).end("write failed");
        console.error("Не удалось сохранить файл:", error.message);
        return;
      }
      res.writeHead(201, { "Content-Type": "text/plain; charset=utf-8" }).end("saved");
      console.log(`Получен файл: ${path.basename(target)} (${size} байт)`);
    });
  });
});

server.listen(8765, "0.0.0.0", () => {
  console.log("Приёмник TXT запущен на порту 8765 — Ctrl+C для остановки");
  console.log(`Файлы сохраняются в: ${outputDir}`);
});

server.on("error", (error) => {
  console.error("Ошибка HTTPS-сервера:", error.message);
  process.exitCode = 1;
});

