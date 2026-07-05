import { Component, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router } from '@angular/router';
import {
  EligibilityCriterionResponse,
  EligibilityCriterionService,
} from '../../../core/subjects/eligibility-criterion.service';
import { SiteResponse, SiteService } from '../../../core/sites/site.service';
import { StudyResponse, StudyService } from '../../../core/studies/study.service';
import { SubjectService } from '../../../core/subjects/subject.service';

@Component({
  selector: 'app-subject-enroll',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatCheckboxModule],
  templateUrl: './subject-enroll.component.html',
})
export class SubjectEnrollComponent implements OnInit {
  readonly studies = signal<StudyResponse[]>([]);
  readonly sites = signal<SiteResponse[]>([]);
  readonly criteria = signal<EligibilityCriterionResponse[]>([]);
  readonly criteriaAnswers = signal<Record<number, boolean>>({});
  readonly errorMessage = signal<string | null>(null);
  readonly violations = signal<string[]>([]);

  readonly form = new FormGroup({
    studyId: new FormControl<number | null>(null, { validators: Validators.required }),
    siteId: new FormControl<number | null>(null, { validators: Validators.required }),
    firstName: new FormControl('', { nonNullable: true, validators: Validators.required }),
    lastName: new FormControl('', { nonNullable: true, validators: Validators.required }),
    dateOfBirth: new FormControl('', { nonNullable: true, validators: Validators.required }),
    gender: new FormControl(''),
    contactPhone: new FormControl(''),
    contactEmail: new FormControl('', { validators: Validators.email }),
    address: new FormControl(''),
    emergencyContact: new FormControl(''),
    notes: new FormControl(''),
    medicalHistory: new FormControl(''),
    screeningDate: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  constructor(
    private readonly studyService: StudyService,
    private readonly siteService: SiteService,
    private readonly criterionService: EligibilityCriterionService,
    private readonly subjectService: SubjectService,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    this.studyService.list(undefined, 0, 100).subscribe((page) => this.studies.set(page.content));
    this.form.controls.studyId.valueChanges.subscribe((studyId) => this.onStudyChange(studyId));
  }

  private onStudyChange(studyId: number | null): void {
    this.sites.set([]);
    this.criteria.set([]);
    this.criteriaAnswers.set({});
    this.form.controls.siteId.setValue(null);
    if (studyId == null) {
      return;
    }
    this.siteService.list(studyId, undefined, 0, 100).subscribe((page) => this.sites.set(page.content));
    this.criterionService.list(studyId).subscribe((criteria) => {
      this.criteria.set(criteria);
      const answers: Record<number, boolean> = {};
      for (const c of criteria) {
        answers[c.id] = false;
      }
      this.criteriaAnswers.set(answers);
    });
  }

  toggleCriterion(criterionId: number, checked: boolean): void {
    this.criteriaAnswers.update((answers) => ({ ...answers, [criterionId]: checked }));
  }

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.errorMessage.set(null);
    this.violations.set([]);
    const raw = this.form.getRawValue();
    const answers = this.criteria().map((c) => ({ criterionId: c.id, met: this.criteriaAnswers()[c.id] ?? false }));

    this.subjectService
      .enroll({
        studyId: raw.studyId!,
        siteId: raw.siteId!,
        firstName: raw.firstName,
        lastName: raw.lastName,
        dateOfBirth: raw.dateOfBirth,
        gender: raw.gender || null,
        contactPhone: raw.contactPhone || null,
        contactEmail: raw.contactEmail || null,
        address: raw.address || null,
        emergencyContact: raw.emergencyContact || null,
        notes: raw.notes || null,
        medicalHistory: raw.medicalHistory || null,
        screeningDate: raw.screeningDate,
        eligibilityAnswers: answers,
      })
      .subscribe({
        next: (subject) => this.router.navigate(['/subjects', subject.id]),
        error: (err) => {
          if (err.status === 400 && err.error?.violations) {
            this.violations.set(err.error.violations);
          } else {
            this.errorMessage.set(err.error?.message ?? 'Could not enroll subject.');
          }
        },
      });
  }
}
