import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { HasRoleDirective } from '../../../core/auth/has-role.directive';
import { Page, TaskResponse, TaskService } from '../../../core/tasks/task.service';
import { StatusChipPipe } from '../../../core/utils/status-chip.pipe';

@Component({
  selector: 'app-task-inbox',
  standalone: true,
  imports: [MatButtonModule, DatePipe, HasRoleDirective, StatusChipPipe],
  templateUrl: './task-inbox.component.html',
})
export class TaskInboxComponent implements OnInit {
  readonly page = signal<Page<TaskResponse> | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly showAllTasks = signal(false);

  constructor(private readonly taskService: TaskService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const request$ = this.showAllTasks() ? this.taskService.allTasks() : this.taskService.myTasks();
    request$.subscribe((page) => this.page.set(page));
  }

  toggleAllTasks(): void {
    this.showAllTasks.update((v) => !v);
    this.load();
  }

  urgencyClass(dueAt: string, status: string): string {
    if (status === 'COMPLETED') {
      return 'chip chip-draft';
    }
    const hoursUntilDue = (new Date(dueAt).getTime() - Date.now()) / (1000 * 60 * 60);
    if (hoursUntilDue < 0) {
      return 'chip chip-critical';
    }
    if (hoursUntilDue < 48) {
      return 'chip chip-pending';
    }
    return 'chip chip-active';
  }

  urgencyLabel(dueAt: string, status: string): string {
    if (status === 'COMPLETED') {
      return 'Completed';
    }
    const hoursUntilDue = (new Date(dueAt).getTime() - Date.now()) / (1000 * 60 * 60);
    if (hoursUntilDue < 0) {
      return 'Overdue';
    }
    if (hoursUntilDue < 48) {
      return 'Due soon';
    }
    return 'On track';
  }

  start(id: number): void {
    this.errorMessage.set(null);
    this.taskService.start(id).subscribe({
      next: () => this.load(),
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not start task.'),
    });
  }

  complete(id: number): void {
    this.errorMessage.set(null);
    this.taskService.complete(id).subscribe({
      next: () => this.load(),
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not complete task.'),
    });
  }
}
