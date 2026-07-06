import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute } from '@angular/router';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { HasRoleDirective } from '../../../core/auth/has-role.directive';
import {
  MonitoringVisitReportResponse,
  MonitoringVisitResponse,
  MonitoringVisitService,
} from '../../../core/monitoring-visits/monitoring-visit.service';
import { ChecklistItemResponse, SiteResponse, SiteService } from '../../../core/sites/site.service';
import { UserService, UserSummaryResponse } from '../../../core/users/user.service';
import { fromIsoDate, toIsoDate } from '../../../core/utils/date-utils';
import { StatusChipPipe } from '../../../core/utils/status-chip.pipe';
import { SiteActivationDialogComponent, SiteActivationResult } from '../site-activation-dialog/site-activation-dialog.component';

const ITEM_LABELS: Record<string, string> = {
  FEASIBILITY_COMPLETION: 'Feasibility Completion',
  IRB_EC_APPROVAL: 'IRB/EC Approval',
  CONTRACT_COMPLETION: 'Contract Completion',
  ESSENTIAL_DOCUMENTS_SUBMISSION: 'Essential Documents Submission',
  SITE_INITIATION_VISIT: 'Site Initiation Visit',
};

@Component({
  selector: 'app-site-detail',
  standalone: true,
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatAutocompleteModule,
    MatDatepickerModule,
    MatDialogModule,
    ReactiveFormsModule,
    DatePipe,
    HasRoleDirective,
    StatusChipPipe,
  ],
  templateUrl: './site-detail.component.html',
})
export class SiteDetailComponent implements OnInit {
  readonly site = signal<SiteResponse | null>(null);
  readonly checklist = signal<ChecklistItemResponse[]>([]);
  readonly errorMessage = signal<string | null>(null);
  readonly missingItems = signal<string[]>([]);
  readonly editingItemType = signal<string | null>(null);
  readonly craUsernameControl = new FormControl('', { nonNullable: true });
  readonly backupCraUsernameControl = new FormControl('', { nonNullable: true });
  readonly craSuggestions = signal<UserSummaryResponse[]>([]);
  readonly backupCraSuggestions = signal<UserSummaryResponse[]>([]);

  readonly editForm = new FormGroup({
    completedDate: new FormControl<Date | null>(null),
    note: new FormControl('', { nonNullable: true }),
  });

  readonly monitoringVisits = signal<MonitoringVisitResponse[]>([]);
  readonly monitoringVisitErrorMessage = signal<string | null>(null);
  readonly showMonitoringVisitForm = signal(false);
  readonly reportsByVisitId = signal<Record<number, MonitoringVisitReportResponse[]>>({});

  readonly monitoringVisitForm = new FormGroup({
    visitType: new FormControl<'SIV' | 'IMV' | 'COV'>('IMV', { nonNullable: true }),
    visitDate: new FormControl<Date | null>(null, { validators: Validators.required }),
    findings: new FormControl<string | null>(null),
    issuesIdentified: new FormControl<string | null>(null),
    checklistNotes: new FormControl<string | null>(null),
  });

  private siteId!: number;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly siteService: SiteService,
    private readonly userService: UserService,
    private readonly monitoringVisitService: MonitoringVisitService,
    private readonly snackBar: MatSnackBar,
    private readonly dialog: MatDialog,
  ) {}

  ngOnInit(): void {
    this.siteId = Number(this.route.snapshot.paramMap.get('id'));
    this.load();

    this.craUsernameControl.valueChanges
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((value) => this.userService.searchByRole('CRA_MONITOR', value.trim())),
      )
      .subscribe((users) => this.craSuggestions.set(users));

    this.backupCraUsernameControl.valueChanges
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((value) => this.userService.searchByRole('CRA_MONITOR', value.trim())),
      )
      .subscribe((users) => this.backupCraSuggestions.set(users));
  }

  load(): void {
    this.siteService.get(this.siteId).subscribe((s) => this.site.set(s));
    this.siteService.checklist(this.siteId).subscribe((items) => this.checklist.set(items));
    this.loadMonitoringVisits();
  }

  loadMonitoringVisits(): void {
    this.monitoringVisitService.list(this.siteId).subscribe((visits) => {
      this.monitoringVisits.set(visits);
      visits.forEach((v) => this.loadReports(v.id));
    });
  }

  loadReports(visitId: number): void {
    this.monitoringVisitService.reports(visitId).subscribe((reports) => {
      this.reportsByVisitId.update((map) => ({ ...map, [visitId]: reports }));
    });
  }

  reportsFor(visitId: number): MonitoringVisitReportResponse[] {
    return this.reportsByVisitId()[visitId] ?? [];
  }

  openMonitoringVisitForm(): void {
    this.monitoringVisitErrorMessage.set(null);
    this.monitoringVisitForm.reset({
      visitType: 'IMV',
      visitDate: new Date(),
      findings: null,
      issuesIdentified: null,
      checklistNotes: null,
    });
    this.showMonitoringVisitForm.set(true);
  }

  cancelMonitoringVisitForm(): void {
    this.showMonitoringVisitForm.set(false);
  }

  submitMonitoringVisit(): void {
    if (this.monitoringVisitForm.invalid) {
      return;
    }
    const raw = this.monitoringVisitForm.getRawValue();
    this.monitoringVisitErrorMessage.set(null);
    this.monitoringVisitService
      .log({
        siteId: this.siteId,
        visitType: raw.visitType,
        visitDate: toIsoDate(raw.visitDate)!,
        findings: raw.findings || null,
        issuesIdentified: raw.issuesIdentified || null,
        checklistNotes: raw.checklistNotes || null,
      })
      .subscribe({
        next: () => {
          this.showMonitoringVisitForm.set(false);
          this.loadMonitoringVisits();
        },
        error: (err) => this.monitoringVisitErrorMessage.set(err.error?.message ?? 'Could not log monitoring visit.'),
      });
  }

  uploadMonitoringVisitReport(visitId: number, input: HTMLInputElement): void {
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.monitoringVisitErrorMessage.set(null);
    this.monitoringVisitService.uploadReport(visitId, file).subscribe({
      next: () => {
        input.value = '';
        this.loadReports(visitId);
      },
      error: (err) => this.monitoringVisitErrorMessage.set(err.error?.message ?? 'Could not upload report.'),
    });
  }

  downloadMonitoringVisitReport(report: MonitoringVisitReportResponse): void {
    this.monitoringVisitService.downloadReport(report.id).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = report.fileName;
      anchor.click();
      URL.revokeObjectURL(url);
    });
  }

  label(itemType: string): string {
    return ITEM_LABELS[itemType] ?? itemType;
  }

  startEdit(item: ChecklistItemResponse): void {
    this.editingItemType.set(item.itemType);
    this.editForm.setValue({
      completedDate: item.completedDate ? fromIsoDate(item.completedDate) : new Date(),
      note: item.note ?? '',
    });
  }

  cancelEdit(): void {
    this.editingItemType.set(null);
  }

  saveEdit(itemType: string): void {
    const { completedDate, note } = this.editForm.getRawValue();
    this.errorMessage.set(null);
    this.siteService
      .updateChecklistItem(this.siteId, itemType, { status: 'COMPLETE', completedDate: toIsoDate(completedDate), note: note || null })
      .subscribe({
        next: () => {
          this.editingItemType.set(null);
          this.load();
        },
        error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not update checklist item.'),
      });
  }

  reopenItem(itemType: string): void {
    this.errorMessage.set(null);
    this.siteService.updateChecklistItem(this.siteId, itemType, { status: 'PENDING', completedDate: null, note: null }).subscribe({
      next: () => this.load(),
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not update checklist item.'),
    });
  }

  attemptActivation(): void {
    const dialogRef = this.dialog.open(SiteActivationDialogComponent, { width: '480px' });
    dialogRef.afterClosed().subscribe((result: SiteActivationResult | undefined) => {
      if (!result) {
        return;
      }
      this.errorMessage.set(null);
      this.missingItems.set([]);
      this.siteService.attemptActivation(this.siteId, result.password, result.reason).subscribe({
        next: () => {
          this.load();
          this.snackBar.open('Site activated.', 'Dismiss', { duration: 4000 });
        },
        error: (err) => {
          if (err.status === 400 && err.error?.missingItems) {
            this.missingItems.set(err.error.missingItems);
          } else {
            const message = err.status === 401 ? 'Incorrect password. Please try again.' : (err.error?.message ?? 'Activation attempt failed.');
            this.errorMessage.set(message);
          }
        },
      });
    });
  }

  selectCra(username: string): void {
    this.craUsernameControl.setValue(username);
  }

  selectBackupCra(username: string): void {
    this.backupCraUsernameControl.setValue(username);
  }

  assignCra(): void {
    const craUsername = this.craUsernameControl.value.trim();
    if (!craUsername) {
      return;
    }
    const backupCraUsername = this.backupCraUsernameControl.value.trim() || null;
    this.errorMessage.set(null);
    this.siteService.assignCra(this.siteId, craUsername, backupCraUsername).subscribe({
      next: () => {
        this.craUsernameControl.setValue('');
        this.backupCraUsernameControl.setValue('');
        this.load();
        this.snackBar.open(`CRA "${craUsername}" assigned.`, 'Dismiss', { duration: 4000 });
      },
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not assign CRA.'),
    });
  }
}
