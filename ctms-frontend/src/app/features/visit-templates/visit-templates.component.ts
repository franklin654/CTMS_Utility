import { Component, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute } from '@angular/router';
import { StudyResponse, StudyService } from '../../core/studies/study.service';
import { VisitTemplateResponse, VisitTemplateService } from '../../core/visits/visit-template.service';

@Component({
  selector: 'app-visit-templates',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  templateUrl: './visit-templates.component.html',
})
export class VisitTemplatesComponent implements OnInit {
  readonly study = signal<StudyResponse | null>(null);
  readonly templates = signal<VisitTemplateResponse[]>([]);
  readonly errorMessage = signal<string | null>(null);
  readonly editingId = signal<number | null>(null);
  private studyId!: number;

  readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: Validators.required }),
    sequenceNumber: new FormControl(1, { nonNullable: true, validators: [Validators.required, Validators.min(1)] }),
    targetDay: new FormControl(0, { nonNullable: true, validators: [Validators.required, Validators.min(0)] }),
    windowEarlyDays: new FormControl(0, { nonNullable: true, validators: [Validators.required, Validators.min(0)] }),
    windowLateDays: new FormControl(0, { nonNullable: true, validators: [Validators.required, Validators.min(0)] }),
    requiredProcedures: new FormControl<string | null>(null),
    visitType: new FormControl<'ONSITE' | 'REMOTE'>('ONSITE', { nonNullable: true }),
  });

  constructor(
    private readonly route: ActivatedRoute,
    private readonly studyService: StudyService,
    private readonly templateService: VisitTemplateService,
  ) {}

  ngOnInit(): void {
    this.studyId = Number(this.route.snapshot.paramMap.get('studyId'));
    this.studyService.get(this.studyId).subscribe((s) => this.study.set(s));
    this.load();
  }

  load(): void {
    this.templateService.list(this.studyId).subscribe((templates) => this.templates.set(templates));
  }

  edit(template: VisitTemplateResponse): void {
    this.editingId.set(template.id);
    this.errorMessage.set(null);
    this.form.setValue({
      name: template.name,
      sequenceNumber: template.sequenceNumber,
      targetDay: template.targetDay,
      windowEarlyDays: template.windowEarlyDays,
      windowLateDays: template.windowLateDays,
      requiredProcedures: template.requiredProcedures,
      visitType: template.visitType,
    });
  }

  cancelEdit(): void {
    this.editingId.set(null);
    this.resetForm();
  }

  save(): void {
    if (this.form.invalid) {
      return;
    }
    this.errorMessage.set(null);
    const req = this.form.getRawValue();
    const editingId = this.editingId();
    const request$ = editingId
      ? this.templateService.update(editingId, req)
      : this.templateService.create({ studyId: this.studyId, ...req });

    request$.subscribe({
      next: () => {
        this.editingId.set(null);
        this.resetForm();
        this.load();
      },
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not save visit template.'),
    });
  }

  deactivate(id: number): void {
    this.errorMessage.set(null);
    this.templateService.deactivate(id).subscribe({
      next: () => this.load(),
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not deactivate visit template.'),
    });
  }

  private resetForm(): void {
    this.form.reset({
      name: '',
      sequenceNumber: (this.templates().length || 0) + 1,
      targetDay: 0,
      windowEarlyDays: 0,
      windowLateDays: 0,
      requiredProcedures: null,
      visitType: 'ONSITE',
    });
  }
}
