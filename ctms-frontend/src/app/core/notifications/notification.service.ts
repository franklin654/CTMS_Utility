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
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  constructor(private readonly http: HttpClient) {}

  list(unreadOnly = false): Observable<Page<NotificationItem>> {
    return this.http.get<Page<NotificationItem>>('/api/notifications', {
      params: { unreadOnly },
    });
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
