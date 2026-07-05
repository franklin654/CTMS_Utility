import { Component, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { StudyService } from '../../../core/studies/study.service';

@Component({
  selector: 'app-study-create',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './study-create.component.html',
})
export class StudyCreateComponent {
  readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: Validators.required }),
    protocolId: new FormControl('', { nonNullable: true, validators: Validators.required }),
    protocolVersion: new FormControl('', { nonNullable: true, validators: Validators.required }),
    phase: new FormControl('', { nonNullable: true, validators: Validators.required }),
    sponsor: new FormControl('', { nonNullable: true, validators: Validators.required }),
    plannedStartDate: new FormControl<string | null>(null),
    plannedEndDate: new FormControl<string | null>(null),
    description: new FormControl<string | null>(null),
  });

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
    this.studyService.create(this.form.getRawValue()).subscribe({
      next: (study) => this.router.navigate(['/studies', study.id]),
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not create study.'),
    });
  }
}
