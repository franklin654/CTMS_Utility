import { Component, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router } from '@angular/router';
import { toIsoDate } from '../../../core/utils/date-utils';
import { StudyService } from '../../../core/studies/study.service';

export const STUDY_PHASES = ['PHASE_I', 'PHASE_II', 'PHASE_III', 'PHASE_IV'];

/** Cross-field: planned end date, if set, must be strictly after planned start date. */
export function plannedDateRangeValidator(group: AbstractControl): ValidationErrors | null {
  const start = group.get('plannedStartDate')?.value as Date | null;
  const end = group.get('plannedEndDate')?.value as Date | null;
  if (start && end && end <= start) {
    return { plannedEndBeforeStart: true };
  }
  return null;
}

function notInPastValidator(control: AbstractControl): ValidationErrors | null {
  const value = control.value as Date | null;
  if (!value) {
    return null;
  }
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return value < today ? { pastDate: true } : null;
}

@Component({
  selector: 'app-study-create',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatDatepickerModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  templateUrl: './study-create.component.html',
})
export class StudyCreateComponent {
  readonly phases = STUDY_PHASES;

  readonly form = new FormGroup(
    {
      name: new FormControl('', { nonNullable: true, validators: Validators.required }),
      protocolId: new FormControl('', { nonNullable: true, validators: Validators.required }),
      protocolVersion: new FormControl('', { nonNullable: true, validators: Validators.required }),
      phase: new FormControl('', { nonNullable: true, validators: Validators.required }),
      sponsor: new FormControl('', { nonNullable: true, validators: Validators.required }),
      plannedStartDate: new FormControl<Date | null>(null, { validators: notInPastValidator }),
      plannedEndDate: new FormControl<Date | null>(null),
      description: new FormControl<string | null>(null),
    },
    { validators: plannedDateRangeValidator },
  );

  readonly errorMessage = signal<string | null>(null);

  constructor(
    private readonly studyService: StudyService,
    private readonly router: Router,
  ) {}

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.errorMessage.set(null);
    const raw = this.form.getRawValue();
    this.studyService
      .create({
        ...raw,
        plannedStartDate: toIsoDate(raw.plannedStartDate),
        plannedEndDate: toIsoDate(raw.plannedEndDate),
      })
      .subscribe({
        next: (study) => this.router.navigate(['/studies', study.id]),
        error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not create study.'),
      });
  }
}
