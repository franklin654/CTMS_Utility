import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { AuditLogResponse, AuditLogService, TraceabilityResponse } from '../../../core/audit/audit-log.service';

@Component({
  selector: 'app-audit-log',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatPaginatorModule, DatePipe],
  templateUrl: './audit-log.component.html',
})
export class AuditLogComponent implements OnInit {
  readonly entityName = new FormControl('', { nonNullable: true });
  readonly entityId = new FormControl('', { nonNullable: true });
  readonly entries = signal<AuditLogResponse[]>([]);
  readonly totalElements = signal(0);
  readonly pageSize = signal(50);
  readonly pageIndex = signal(0);
  readonly traceability = signal<TraceabilityResponse | null>(null);
  readonly traceabilityErrorMessage = signal<string | null>(null);

  constructor(private readonly auditLogService: AuditLogService) {}

  ngOnInit(): void {
    this.search();
  }

  search(): void {
    this.pageIndex.set(0);
    this.load();
  }

  load(): void {
    this.auditLogService
      .search(this.entityName.value, this.entityId.value, this.pageIndex(), this.pageSize())
      .subscribe((page) => {
        this.entries.set(page.content);
        this.totalElements.set(page.totalElements);
      });
  }

  onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  exportCsv(): void {
    this.auditLogService.exportCsv(this.entityName.value, this.entityId.value).subscribe((csv) => {
      const blob = new Blob([csv], { type: 'text/csv' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'audit-log-export.csv';
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  get canViewTraceability(): boolean {
    return !!this.entityName.value.trim() && !!this.entityId.value.trim();
  }

  viewTraceability(): void {
    this.traceabilityErrorMessage.set(null);
    this.auditLogService.traceability(this.entityName.value.trim(), this.entityId.value.trim()).subscribe({
      next: (result) => this.traceability.set(result),
      error: (err) => this.traceabilityErrorMessage.set(err.error?.message ?? 'Could not load traceability report.'),
    });
  }

  closeTraceability(): void {
    this.traceability.set(null);
    this.traceabilityErrorMessage.set(null);
  }
}
