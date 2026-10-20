export type SocketTask = {
  cancel: () => void
}

export function encodeSearchPayload(key: string): string

export function createSocketTask(
  socket: WebSocket,
  handlers: {
    onOpen?: (socket: WebSocket) => void
    onMessage?: (data: unknown) => void
    onError?: (error: unknown) => void
    onFinish: () => void
  },
): SocketTask
