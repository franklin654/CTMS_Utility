import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { PatientVisitService } from '../../../core/patient/patient-visit.service';
import { SubjectVisitScheduleResponse } from '../../../core/visits/visit.service';

@Component({
  selector: 'app-patient-visits',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
  templateUrl: './patient-visits.component.html',
})
export class PatientVisitsComponent implements OnInit {
  readonly schedule = signal<SubjectVisitScheduleResponse | null>(null);
  readonly errorMessage = signal<string | null>(null);

  constructor(private readonly patientVisitService: PatientVisitService) {}

  ngOnInit(): void {
    this.patientVisitService.mySchedule().subscribe({
      next: (s) => this.schedule.set(s),
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not load your visit schedule.'),
    });
  }
}
