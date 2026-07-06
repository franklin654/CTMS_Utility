import { Component, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { PatientProfileService } from '../../../core/patient/patient-profile.service';
import { SubjectResponse } from '../../../core/subjects/subject.service';

@Component({
  selector: 'app-patient-profile',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './patient-profile.component.html',
})
export class PatientProfileComponent implements OnInit {
  readonly profile = signal<SubjectResponse | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  readonly form = new FormGroup({
    contactPhone: new FormControl<string | null>(null),
    contactEmail: new FormControl<string | null>(null),
    address: new FormControl<string | null>(null),
    emergencyContact: new FormControl<string | null>(null),
  });

  constructor(private readonly patientProfileService: PatientProfileService) {}

  ngOnInit(): void {
    this.patientProfileService.get().subscribe({
      next: (p) => {
        this.profile.set(p);
        this.form.setValue({
          contactPhone: p.contactPhone,
          contactEmail: p.contactEmail,
          address: p.address,
          emergencyContact: p.emergencyContact,
        });
      },
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not load your profile.'),
    });
  }

  save(): void {
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.patientProfileService.update(this.form.getRawValue()).subscribe({
      next: (p) => {
        this.profile.set(p);
        this.successMessage.set('Profile updated.');
      },
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not update profile.'),
    });
  }
}
