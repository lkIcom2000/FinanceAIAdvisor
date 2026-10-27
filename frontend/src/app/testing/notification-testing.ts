import { Notification } from '../api/models/notification';

/** An unread "For your information" Notification with the given id, for specs. */
export function notification(id: string, overrides: Partial<Notification> = {}): Notification {
  return {
    id,
    status: 'FOR_YOUR_INFORMATION',
    title: `Title ${id}`,
    body: `Body ${id}`,
    date: '2026-10-15',
    figures: [],
    read: false,
    undoable: false,
    ...overrides,
  };
}
