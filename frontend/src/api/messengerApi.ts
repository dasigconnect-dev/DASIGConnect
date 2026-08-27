import { api } from "./authApi";

export interface MessengerConnection {
  connected: boolean;
  enabled: boolean;
  linkedAt: string | null;
}

export interface MessengerLinkCode {
  code: string;
  expiresAt: string;
}

export async function getMessengerConnectionStatus(): Promise<MessengerConnection> {
  const res = await api.get<MessengerConnection>("/integrations/messenger/connection");
  return res.data;
}

export async function createMessengerLinkCode(): Promise<MessengerLinkCode> {
  const res = await api.post<MessengerLinkCode>("/integrations/messenger/connection/link-code");
  return res.data;
}

export async function disconnectMessenger(): Promise<void> {
  await api.delete("/integrations/messenger/connection");
}
