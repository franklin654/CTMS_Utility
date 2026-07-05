import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { NotificationItem, NotificationService } from '../../../core/notifications/notification.service';

const KNOWN_TYPES = [
  'TASK_ASSIGNED',
  'TASK_ESCALATED',
  'VISIT_DUE_TOMORROW',
  'VISIT_OVERDUE',
  'VISIT_MISSED',
  'SUBJECT_STATE_CHANGE',
  'SITE_ACTIVATED',
];

@Component({
  selector: 'app-notification-list',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatSelectModule, DatePipe],
  templateUrl: './notification-list.component.html',
})
export class NotificationListComponent implements OnInit {
  readonly knownTypes = KNOWN_TYPES;
  readonly notifications = signal<NotificationItem[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);

  readonly typeControl = new FormControl<string>('', { nonNullable: true });
  readonly unreadOnlyControl = new FormControl(false, { nonNullable: true });

  constructor(private readonly notificationService: NotificationService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.notificationService
      .list(this.unreadOnlyControl.value, this.typeControl.value || undefined, this.page(), 20)
      .subscribe((p) => {
        this.notifications.set(p.content);
        this.totalPages.set(p.totalPages);
      });
  }

  applyFilters(): void {
    this.page.set(0);
    this.load();
  }

  nextPage(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.page.update((p) => p + 1);
      this.load();
    }
  }

  prevPage(): void {
    if (this.page() > 0) {
      this.page.update((p) => p - 1);
      this.load();
    }
  }

  markRead(notification: NotificationItem): void {
    if (notification.read) {
      return;
    }
    this.notificationService.markRead(notification.id).subscribe(() => {
      notification.read = true;
    });
  }

  markAllRead(): void {
    this.notificationService.markAllRead().subscribe(() => this.load());
  }
}
