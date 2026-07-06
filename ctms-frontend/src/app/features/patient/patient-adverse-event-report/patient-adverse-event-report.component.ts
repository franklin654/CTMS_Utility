import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { AdverseEventResponse } from '../../../core/adverse-events/adverse-event.service';
import { PatientAdverseEventService } from '../../../core/patient/patient-adverse-event.service';

@Component({
  selector: 'app-patient-adverse-event-report',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, DatePipe],
  templateUrl: './patient-adverse-event-report.component.html',
})
export class PatientAdverseEventReportComponent implements OnInit {
  readonly reports = signal<AdverseEventResponse[]>([]);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly form = new FormGroup({
    description: new FormControl('', { nonNullable: true, validators: Validators.required }),
    severity: new FormControl<'MILD' | 'MODERATE' | 'SEVERE' | 'LIFE_THREATENING'>('MILD', { nonNullable: true }),
  });

  constructor(private readonly patientAdverseEventService: PatientAdverseEventService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.patientAdverseEventService.list().subscribe({
      next: (reports) => this.reports.set(reports),
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not load your reports.'),
    });
  }

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.patientAdverseEventService.report(this.form.getRawValue()).subscribe({
      next: () => {
        this.successMessage.set('Reported to your care team.');
        this.form.reset({ description: '', severity: 'MILD' });
        this.load();
      },
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not submit report.'),
    });
  }
}
