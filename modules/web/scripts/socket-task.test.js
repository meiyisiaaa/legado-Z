import assert from 'node:assert/strict'
import test from 'node:test'
import { createSocketTask, encodeSearchPayload } from '../src/api/socketTask.js'

class FakeSocket {
  readyState = 0
  onopen = null
  onmessage = null
  onerror = null
  onclose = null
  sent = []
  closeCalls = 0

  send(value) {
    this.sent.push(value)
  }

  close() {
    this.closeCalls += 1
    this.readyState = 3
  }

  emitOpen() {
    this.readyState = 1
    this.onopen?.()
  }

  emitMessage(data) {
    this.onmessage?.({ data })
  }
}

test('search payload preserves quotes, slashes and non-ASCII text', () => {
  const key = '山“海”\\第 1 章'
  assert.deepEqual(JSON.parse(encodeSearchPayload(key)), { key })
})

test('socket message errors finish once, close and release handlers', () => {
  const socket = new FakeSocket()
  let errors = 0
  let finishes = 0
  const task = createSocketTask(socket, {
    onMessage: () => {
      throw new Error('invalid search frame')
    },
    onError: () => errors++,
    onFinish: () => finishes++,
  })

  socket.emitMessage('invalid')
  task.cancel()

  assert.equal(errors, 1)
  assert.equal(finishes, 1)
  assert.equal(socket.closeCalls, 1)
  assert.equal(socket.onmessage, null)
  assert.equal(socket.onerror, null)
})

test('cancelling before open releases the pending connection', () => {
  const socket = new FakeSocket()
  let finishes = 0
  const task = createSocketTask(socket, { onFinish: () => finishes++ })

  task.cancel()
  task.cancel()

  assert.equal(finishes, 1)
  assert.equal(socket.closeCalls, 1)
  assert.equal(socket.onopen, null)
})
