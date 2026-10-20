/** @typedef {{ cancel: () => void }} SocketTask */

/** @param {string} key */
export const encodeSearchPayload = key => JSON.stringify({ key })

/**
 * Owns a WebSocket's event handlers and closes it exactly once on error or cancellation.
 * @param {WebSocket} socket
 * @param {{ onOpen?: (socket: WebSocket) => void, onMessage?: (data: unknown) => void, onError?: (error: unknown) => void, onFinish: () => void }} handlers
 * @returns {SocketTask}
 */
export const createSocketTask = (socket, handlers) => {
  let finished = false

  const finish = (closeSocket = false) => {
    if (finished) return
    finished = true

    socket.onopen = null
    socket.onmessage = null
    socket.onerror = null
    socket.onclose = null

    if (closeSocket && (socket.readyState === 0 || socket.readyState === 1)) {
      try {
        socket.close(1000, 'client finished')
      } catch {
        // The socket is already released from our handlers; close may fail during teardown.
      }
    }

    handlers.onFinish()
  }

  const fail = error => {
    try {
      handlers.onError?.(error)
    } catch {
      // A reporting callback must not interrupt socket cleanup.
    } finally {
      finish(true)
    }
  }

  socket.onopen = () => {
    try {
      handlers.onOpen?.(socket)
    } catch (error) {
      fail(error)
    }
  }
  socket.onmessage = event => {
    try {
      handlers.onMessage?.(event.data)
    } catch (error) {
      fail(error)
    }
  }
  socket.onerror = event => fail(event)
  socket.onclose = () => finish()

  return {
    cancel: () => finish(true),
  }
}
