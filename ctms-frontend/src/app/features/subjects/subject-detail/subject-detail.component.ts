import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute } from '@angular/router';
import { HasRoleDirective } from '../../../core/auth/has-role.directive';
import {
  SubjectResponse,
  SubjectService,
  SubjectStatusHistoryResponse,
} from '../../../core/subjects/subject.service';

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
  imports: [MatButtonModule, MatFormFieldModule, MatInputModule, ReactiveFormsModule, DatePipe, HasRoleDirective],
  templateUrl: './subject-detail.component.html',
})
export class SubjectDetailComponent implements OnInit {
  readonly subject = signal<SubjectResponse | null>(null);
  readonly history = signal<SubjectStatusHistoryResponse[]>([]);
  readonly errorMessage = signal<string | null>(null);
  readonly editing = signal(false);
  readonly showWithdrawForm = signal(false);

  readonly justificationControl = new FormControl('', { nonNullable: true, validators: Validators.required });
  readonly reasonCodeControl = new FormControl('', { nonNullable: true, validators: Validators.required });

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
  ) {}

  ngOnInit(): void {
    this.subjectId = Number(this.route.snapshot.paramMap.get('id'));
    this.load();
  }

  load(): void {
    this.subjectService.get(this.subjectId).subscribe((s) => this.subject.set(s));
    this.subjectService.history(this.subjectId).subscribe((h) => this.history.set(h));
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
