#!/usr/bin/env node
'use strict'

const readline = require('node:readline')
const [modulePath, portText, minecraft] = process.argv.slice(2)
if (!modulePath || !/^\d+$/.test(portText || '') || !['1.21.11', '26.2', '26.3'].includes(minecraft)) {
  throw new Error('Usage: pinyin_startup_client.js <pinned-protocol-module> <loopback-port> <minecraft>')
}
const version = require(modulePath + '/package.json').version
if (version !== '1.66.2+complexity.26.2.3') throw new Error('Unexpected pinned protocol fixture: ' + version)
const protocol = require(modulePath)
const data = require(require.resolve('minecraft-data', { paths: [modulePath] }))
let client
let joined = false
let requestedQuit = false
let ticks
const deadline = setTimeout(() => fail(new Error('Connected Pinyin probe exceeded 120 seconds')), 120000)

function fail (error) {
  console.error(error.stack || String(error))
  process.exitCode = 1
  if (client) client.end('Pinyin client failed')
  else process.exit(1)
}

protocol.ping({ host: '127.0.0.1', port: Number(portText) }, (error, response) => {
  if (error) return fail(error)
  // A milestone may share an existing wire protocol. Only use a mapping whose
  // numeric protocol exactly matches the real server; never change the server lane.
  const wireVersion = [minecraft, '26.2', '1.21.11'].find(name => {
    try { return data(name)?.version?.version === response.version.protocol } catch { return false }
  })
  if (!wireVersion) return fail(new Error('Pinned client does not support server protocol ' + response.version.protocol))
  console.log('JEG client protocol:', wireVersion, response.version.protocol, response.version.name)
  client = protocol.createClient({
    host: '127.0.0.1', port: Number(portText), username: 'JEGPinyinBot', auth: 'offline', version: wireVersion
  })
  let position = { x: 0, y: 0, z: 0, yaw: 0, pitch: 0 }
  ticks = setInterval(() => {
    if (client.state === 'play') client.write('tick_end', {})
  }, 50)
  client.on('login', () => {
    joined = true
    console.log('JEG client reached play:', client.username, client.uuid)
  })
  client.on('position', packet => {
    for (const key of ['x', 'y', 'z', 'yaw', 'pitch']) {
      position[key] = packet[key] + (packet.flags[key] ? position[key] : 0)
    }
    client.write('teleport_confirm', { teleportId: packet.teleportId })
    client.write('position_look', { ...position, flags: { onGround: false, hasHorizontalCollision: false } })
  })
  client.on('error', fail)
  client.on('disconnect', packet => console.error('Server disconnect:', JSON.stringify(packet)))
  client.on('end', reason => {
    clearTimeout(deadline)
    clearInterval(ticks)
    if (!joined || !requestedQuit) {
      console.error('Unexpected client disconnect:', reason)
      process.exitCode = 1
    }
    process.exit(process.exitCode || 0)
  })
})
readline.createInterface({ input: process.stdin }).on('line', line => {
  if (line !== 'quit' || !client || !joined) return fail(new Error('Unexpected client control input'))
  requestedQuit = true
  client.end('Pinyin client complete')
})
