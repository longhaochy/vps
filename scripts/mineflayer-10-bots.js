#!/usr/bin/env node
'use strict';

const mineflayer = require('mineflayer');

const HOST = process.argv[2] || process.env.MC_HOST || 'localhost';
const PORT = Number.parseInt(process.argv[3] || process.env.MC_PORT || '2025', 10);
const VERSION = process.env.MC_VERSION || false;
const AUTH = process.env.MC_AUTH || 'offline';
const BOT_COUNT = Number.parseInt(process.argv[4] || process.env.BOT_COUNT || '20', 10);
const JOIN_DELAY_MS = Number.parseInt(process.env.JOIN_DELAY_MS || '500', 10);
const RECONNECT = process.env.RECONNECT === '1';
const RECONNECT_DELAY_MS = Number.parseInt(process.env.RECONNECT_DELAY_MS || '45000', 10);
const JUMP_ON_SPAWN = process.env.JUMP_ON_SPAWN === '1';

const BOT_NAMES = [
  'RizkyNaufal', 'DimasPratama', 'FajarSaputra', 'BagasAditya', 'RamaPutra',
  'AldiFirmansyah', 'NandaWijaya', 'YogaMaulana', 'IlhamAkbar', 'ArifSetiawan',
  'KevinSantoso', 'AndikaPratama', 'RezaRamadhan', 'BayuSetiadi', 'HendraGunawan',
  'DickyHidayat', 'FauzanAziz', 'RianHidayat', 'SatriaWibowo', 'BudiSantoso',
  'AndiWijaya', 'DediKurniawan', 'EkoPrasetyo', 'GalihPermana', 'HarisNugroho',
  'IwanFals', 'JokoSusilo', 'KrisnaMurti', 'LukmanHakim', 'MahendraPutra',
  'NaufalFikri', 'OkiPratama', 'PutraRamadhan', 'QoriSyah', 'RahmatHidayat',
  'SandiYusuf', 'TaufikHidayat', 'UmarFaruk', 'VinoSebastian', 'WawanSetiawan',
  'XanderPutra', 'YudhaPratama', 'ZakiAlfarisi', 'AhmadFauzi', 'BimoSakti',
  'CandraDewi', 'Danuarta', 'ErlanggaPutra', 'FerryIrawan', 'GilangRamadhan',
  'HadiSaputra', 'IndraLesmana', 'JefriNichol', 'KurniawanDS', 'LutfiAgizal',
  'MochammadRizki', 'NizamZulkarnain', 'OscarWilde', 'PanjiPetualang', 'QaisarPutra',
  'RakaMahendra', 'SolehSolihun', 'TegarSeptian', 'UjangBustomi', 'VickyNitinegoro',
  'WahyuHidayat', 'XavierPratama', 'YayanRuhian', 'ZulfikarAli', 'AbdiNegara',
  'BambangPamungkas', 'CahyoWidodo', 'DaffaAlif', 'EgaPutra', 'FahmiReza',
  'GunturBumi', 'HuseinJafar', 'IrfanHakim', 'JaluPutra', 'KatonBagaskara',
  'LeonHart', 'MikaelPutra', 'NikoAlHakim', 'ObiWan', 'PashaUngu',
  'QadafiPutra'
];

const activeBots = new Map();

function log(name, message) {
  const time = new Date().toISOString().slice(11, 19);
  console.log(`[${time}] [${name}] ${message}`);
}

function createBot(username, index) {
  const bot = mineflayer.createBot({
    host: HOST,
    port: PORT,
    username,
    auth: AUTH,
    version: VERSION || undefined,
    hideErrors: false,
    checkTimeoutInterval: 60 * 1000
  });

  activeBots.set(username, bot);

  bot.once('spawn', () => {
    log(username, `joined ${HOST}:${PORT}`);
    if (JUMP_ON_SPAWN) {
      bot.setControlState('jump', true);
      setTimeout(() => bot.setControlState('jump', false), 250);
    }
  });

  bot.on('kicked', (reason) => {
    log(username, `kicked: ${formatReason(reason)}`);
  });

  bot.on('end', () => {
    activeBots.delete(username);
    log(username, 'disconnected');
    if (RECONNECT) {
      setTimeout(() => createBot(username, index), RECONNECT_DELAY_MS + index * 1000);
    }
  });

  bot.on('error', (error) => {
    log(username, `error: ${error.message}`);
  });
}

function formatReason(reason) {
  if (typeof reason === 'string') {
    return reason;
  }
  try {
    return JSON.stringify(reason);
  } catch {
    return String(reason);
  }
}

function start() {
  const names = BOT_NAMES.slice(0, Math.max(0, Math.min(BOT_COUNT, BOT_NAMES.length)));
  console.log(`Starting ${names.length} bots for ${HOST}:${PORT}`);
  console.log(`auth=${AUTH} reconnect=${RECONNECT ? 'on' : 'off'} joinDelay=${JOIN_DELAY_MS}ms jumpOnSpawn=${JUMP_ON_SPAWN ? 'on' : 'off'}`);
  if (HOST === 'localhost' || HOST === '127.0.0.1') {
    console.log('Note: localhost only works on the same VPS as the Minecraft server. From another VPS, pass the public/playit host as the first argument.');
  }

  names.forEach((username, index) => {
    setTimeout(() => createBot(username, index), index * JOIN_DELAY_MS);
  });
}

function shutdown() {
  console.log('\nStopping bots...');
  for (const bot of activeBots.values()) {
    bot.quit('Bot shutdown');
  }
  setTimeout(() => process.exit(0), 1500);
}

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

start();
