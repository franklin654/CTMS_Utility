import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute } from '@angular/router';
import { HasRoleDirective } from '../../../core/auth/has-role.directive';
import {
  SubjectResponse,
  SubjectService,
  SubjectStatusHistoryResponse,
} from '../../../core/subjects/subject.service';
import { SubjectVisitScheduleResponse, VisitService } from '../../../core/visits/visit.service';

type VisitActionType = 'complete' | 'miss' | 'reschedule';

const NEXT_STATUS: Record<string, string> = {
  SCREENED: 'ENROLLED',
  ENROLLED: 'IN_TREATMENT',
  IN_TREATMENT: 'COMPLETED',
};

const STATUS_LABELS: Record<string, string> = {
  SCREENED: 'Screened',
  ENROLLED: 'Enrolled',
  IN_TREATMENT: 'In Treatment',
  COMPLETED: 'Completed',
  WITHDRAWN: 'Withdrawn',
};

@Component({
  selector: 'app-subject-detail',
  standalone: true,
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    ReactiveFormsModule,
    DatePipe,
    DecimalPipe,
    HasRoleDirective,
  ],
  templateUrl: './subject-detail.component.html',
})
export class SubjectDetailComponent implements OnInit {
  readonly subject = signal<SubjectResponse | null>(null);
  readonly history = signal<SubjectStatusHistoryResponse[]>([]);
  readonly errorMessage = signal<string | null>(null);
  readonly editing = signal(false);
  readonly showWithdrawForm = signal(false);

  readonly visitSchedule = signal<SubjectVisitScheduleResponse | null>(null);
  readonly visitErrorMessage = signal<string | null>(null);
  readonly openVisitAction = signal<{ visitId: number; type: VisitActionType } | null>(null);
  readonly showAdHocForm = signal(false);

  readonly justificationControl = new FormControl('', { nonNullable: true, validators: Validators.required });
  readonly reasonCodeControl = new FormControl('', { nonNullable: true, validators: Validators.required });

  readonly completeVisitForm = new FormGroup({
    actualDate: new FormControl('', { nonNullable: true, validators: Validators.required }),
    actualTime: new FormControl<string | null>(null),
    notes: new FormControl<string | null>(null),
  });

  readonly missVisitForm = new FormGroup({
    reasonCode: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  readonly rescheduleVisitForm = new FormGroup({
    newDate: new FormControl('', { nonNullable: true, validators: Validators.required }),
    reasonCode: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  readonly adHocVisitForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: Validators.required }),
    scheduledDate: new FormControl('', { nonNullable: true, validators: Validators.required }),
    visitType: new FormControl<'ONSITE' | 'REMOTE'>('ONSITE', { nonNullable: true }),
    requiredProcedures: new FormControl<string | null>(null),
    reasonCode: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  readonly editForm = new FormGroup({
    firstName: new FormControl('', { nonNullable: true, validators: Validators.required }),
    lastName: new FormControl('', { nonNullable: true, validators: Validators.required }),
    gender: new FormControl(''),
    contactPhone: new FormControl(''),
    contactEmail: new FormControl('', { validators: Validators.email }),
    address: new FormControl(''),
    emergencyContact: new FormControl(''),
    notes: new FormControl(''),
    medicalHistory: new FormControl(''),
  });

  private subjectId!: number;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly subjectService: SubjectService,
    private readonly visitService: VisitService,
  ) {}

  ngOnInit(): void {
    this.subjectId = Number(this.route.snapshot.paramMap.get('id'));
    this.load();
  }

  load(): void {
    this.subjectService.get(this.subjectId).subscribe((s) => this.subject.set(s));
    this.subjectService.history(this.subjectId).subscribe((h) => this.history.set(h));
    this.loadVisits();
  }

  loadVisits(): void {
    this.visitService.schedule(this.subjectId).subscribe((schedule) => this.visitSchedule.set(schedule));
  }

  isVisitHighlighted(status: string): boolean {
    return status === 'MISSED' || status === 'RESCHEDULED';
  }

  openComplete(visitId: number): void {
    this.visitErrorMessage.set(null);
    this.completeVisitForm.reset({ actualDate: new Date().toISOString().slice(0, 10), actualTime: null, notes: null });
    this.openVisitAction.set({ visitId, type: 'complete' });
  }

  openMiss(visitId: number): void {
    this.visitErrorMessage.set(null);
    this.missVisitForm.reset({ reasonCode: '' });
    this.openVisitAction.set({ visitId, type: 'miss' });
  }

  openReschedule(visitId: number): void {
    this.visitErrorMessage.set(null);
    this.rescheduleVisitForm.reset({ newDate: '', reasonCode: '' });
    this.openVisitAction.set({ visitId, type: 'reschedule' });
  }

  cancelVisitAction(): void {
    this.openVisitAction.set(null);
  }

  submitComplete(visitId: number): void {
    if (this.completeVisitForm.invalid) {
      return;
    }
    const raw = this.completeVisitForm.getRawValue();
    this.visitErrorMessage.set(null);
    this.visitService
      .complete(visitId, { actualDate: raw.actualDate, actualTime: raw.actualTime || null, notes: raw.notes || null })
      .subscribe({
        next: () => {
          this.openVisitAction.set(null);
          this.loadVisits();
        },
        error: (err) => this.visitErrorMessage.set(err.error?.message ?? 'Could not mark visit completed.'),
      });
  }

  submitMiss(visitId: number): void {
    if (this.missVisitForm.invalid) {
      return;
    }
    this.visitErrorMessage.set(null);
    this.visitService.miss(visitId, { reasonCode: this.missVisitForm.getRawValue().reasonCode }).subscribe({
      next: () => {
        this.openVisitAction.set(null);
        this.loadVisits();
      },
      error: (err) => this.visitErrorMessage.set(err.error?.message ?? 'Could not mark visit missed.'),
    });
  }

  submitReschedule(visitId: number): void {
    if (this.rescheduleVisitForm.invalid) {
      return;
    }
    const raw = this.rescheduleVisitForm.getRawValue();
    this.visitErrorMessage.set(null);
    this.visitService.reschedule(visitId, { newDate: raw.newDate, reasonCode: raw.reasonCode }).subscribe({
      next: () => {
        this.openVisitAction.set(null);
        this.loadVisits();
      },
      error: (err) => this.visitErrorMessage.set(err.error?.message ?? 'Could not reschedule visit.'),
    });
  }

  openAdHocForm(): void {
    this.visitErrorMessage.set(null);
    this.adHocVisitForm.reset({
      name: '',
      scheduledDate: '',
      visitType: 'ONSITE',
      requiredProcedures: null,
      reasonCode: '',
    });
    this.showAdHocForm.set(true);
  }

  cancelAdHocForm(): void {
    this.showAdHocForm.set(false);
  }

  submitAdHoc(): void {
    if (this.adHocVisitForm.invalid) {
      return;
    }
    const raw = this.adHocVisitForm.getRawValue();
    this.visitErrorMessage.set(null);
    this.visitService
      .scheduleAdHoc(this.subjectId, {
        name: raw.name,
        scheduledDate: raw.scheduledDate,
        visitType: raw.visitType,
        requiredProcedures: raw.requiredProcedures || null,
        reasonCode: raw.reasonCode,
      })
      .subscribe({
        next: () => {
          this.showAdHocForm.set(false);
          this.loadVisits();
        },
        error: (err) => this.visitErrorMessage.set(err.error?.message ?? 'Could not schedule ad-hoc visit.'),
      });
  }

  statusLabel(status: string): string {
    return STATUS_LABELS[status] ?? status;
  }

  nextStatus(status: string): string | null {
    return NEXT_STATUS[status] ?? null;
  }

  isWithdrawable(status: string): boolean {
    return status === 'SCREENED' || status === 'ENROLLED' || status === 'IN_TREATMENT';
  }

  advance(): void {
    const s = this.subject();
    const next = s ? this.nextStatus(s.status) : null;
    if (!s || !next || this.justificationControl.invalid) {
      return;
    }
    this.errorMessage.set(null);
    this.subjectService.transition(this.subjectId, next, this.justificationControl.value).subscribe({
      next: () => {
        this.justificationControl.setValue('');
        this.load();
      },
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Transition failed.'),
    });
  }

  confirmWithdraw(): void {
    if (this.reasonCodeControl.invalid) {
      return;
    }
    this.errorMessage.set(null);
    this.subjectService.withdraw(this.subjectId, this.reasonCodeControl.value).subscribe({
      next: () => {
        this.reasonCodeControl.setValue('');
        this.showWithdrawForm.set(false);
        this.load();
      },
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Withdrawal failed.'),
    });
  }

  startEdit(): void {
    const s = this.subject();
    if (!s) {
      return;
    }
    this.editForm.setValue({
      firstName: s.firstName,
      lastName: s.lastName,
      gender: s.gender ?? '',
      contactPhone: s.contactPhone ?? '',
      contactEmail: s.contactEmail ?? '',
      address: s.address ?? '',
      emergencyContact: s.emergencyContact ?? '',
      notes: s.notes ?? '',
      medicalHistory: s.medicalHistory ?? '',
    });
    this.editing.set(true);
  }

  cancelEdit(): void {
    this.editing.set(false);
  }

  saveEdit(): void {
    if (this.editForm.invalid) {
      return;
    }
    const raw = this.editForm.getRawValue();
    this.errorMessage.set(null);
    this.subjectService
      .update(this.subjectId, {
        firstName: raw.firstName,
        lastName: raw.lastName,
        gender: raw.gender || null,
        contactPhone: raw.contactPhone || null,
        contactEmail: raw.contactEmail || null,
        address: raw.address || null,
        emergencyContact: raw.emergencyContact || null,
        notes: raw.notes || null,
        medicalHistory: raw.medicalHistory || null,
      })
      .subscribe({
        next: () => {
          this.editing.set(false);
          this.load();
        },
        error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not update subject.'),
      });
  }
}
