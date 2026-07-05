import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { NotificationItem, NotificationService } from '../../core/notifications/notification.service';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [MatBadgeModule, MatButtonModule, MatMenuModule, DatePipe],
  templateUrl: './notification-bell.component.html',
})
export class NotificationBellComponent implements OnInit {
  readonly unreadCount = signal(0);
  readonly notifications = signal<NotificationItem[]>([]);

  constructor(private readonly notificationService: NotificationService) {}

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.notificationService.unreadCount().subscribe((count) => this.unreadCount.set(count));
  }

  onMenuOpened(): void {
    this.notificationService.list().subscribe((page) => this.notifications.set(page.content));
  }

  markRead(notification: NotificationItem): void {
    if (notification.read) {
      return;
    }
    this.notificationService.markRead(notification.id).subscribe(() => {
      notification.read = true;
      this.refresh();
    });
  }

  markAllRead(): void {
    this.notificationService.markAllRead().subscribe(() => {
      this.notifications.update((items) => items.map((n) => ({ ...n, read: true })));
      this.unreadCount.set(0);
    });
  }
}
