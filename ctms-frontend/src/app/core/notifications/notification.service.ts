import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface NotificationItem {
  id: number;
  type: string;
  title: string;
  body: string | null;
  link: string | null;
  read: boolean;
  createdAt: string;
}

interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  constructor(private readonly http: HttpClient) {}

  list(unreadOnly = false, type?: string, page = 0, size = 20): Observable<Page<NotificationItem>> {
    const params: Record<string, string | number | boolean> = { unreadOnly, page, size };
    if (type) {
      params['type'] = type;
    }
    return this.http.get<Page<NotificationItem>>('/api/notifications', { params });
  }

  unreadCount(): Observable<number> {
    return this.http.get<number>('/api/notifications/unread-count');
  }

  markRead(id: number): Observable<void> {
    return this.http.post<void>(`/api/notifications/${id}/read`, {});
  }

  markAllRead(): Observable<void> {
    return this.http.post<void>('/api/notifications/read-all', {});
  }
}
