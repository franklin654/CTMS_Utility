import { Component, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute } from '@angular/router';
import {
  EligibilityCriterionResponse,
  EligibilityCriterionService,
} from '../../core/subjects/eligibility-criterion.service';
import { StudyResponse, StudyService } from '../../core/studies/study.service';

@Component({
  selector: 'app-eligibility-criteria',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  templateUrl: './eligibility-criteria.component.html',
})
export class EligibilityCriteriaComponent implements OnInit {
  readonly study = signal<StudyResponse | null>(null);
  readonly criteria = signal<EligibilityCriterionResponse[]>([]);
  readonly errorMessage = signal<string | null>(null);
  private studyId!: number;

  readonly form = new FormGroup({
    label: new FormControl('', { nonNullable: true, validators: Validators.required }),
    criterionType: new FormControl<'INCLUSION' | 'EXCLUSION'>('INCLUSION', { nonNullable: true }),
  });

  constructor(
    private readonly route: ActivatedRoute,
    private readonly studyService: StudyService,
    private readonly criterionService: EligibilityCriterionService,
  ) {}

  ngOnInit(): void {
    this.studyId = Number(this.route.snapshot.paramMap.get('studyId'));
    this.studyService.get(this.studyId).subscribe((s) => this.study.set(s));
    this.load();
  }

  load(): void {
    this.criterionService.list(this.studyId).subscribe((criteria) => this.criteria.set(criteria));
  }

  addCriterion(): void {
    if (this.form.invalid) {
      return;
    }
    const { label, criterionType } = this.form.getRawValue();
    this.errorMessage.set(null);
    this.criterionService.create({ studyId: this.studyId, label, criterionType }).subscribe({
      next: () => {
        this.form.reset({ label: '', criterionType: 'INCLUSION' });
        this.load();
      },
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not add criterion.'),
    });
  }

  deactivate(id: number): void {
    this.errorMessage.set(null);
    this.criterionService.deactivate(id).subscribe({
      next: () => this.load(),
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not deactivate criterion.'),
    });
  }
}
