import { Component, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute } from '@angular/router';
import { HasRoleDirective } from '../../core/auth/has-role.directive';
import { DocumentRequirementResponse, DocumentRequirementService } from '../../core/document-requirements/document-requirement.service';
import { StudyResponse, StudyService } from '../../core/studies/study.service';

@Component({
  selector: 'app-document-requirements',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatCheckboxModule, HasRoleDirective],
  templateUrl: './document-requirements.component.html',
})
export class DocumentRequirementsComponent implements OnInit {
  readonly study = signal<StudyResponse | null>(null);
  readonly requirements = signal<DocumentRequirementResponse[]>([]);
  readonly errorMessage = signal<string | null>(null);
  readonly showCreateForm = signal(false);

  private studyId!: number;

  readonly createForm = new FormGroup({
    studyPhase: new FormControl<'ACTIVE' | 'CONDUCT' | 'CLOSEOUT'>('ACTIVE', { nonNullable: true }),
    documentCategory: new FormControl('', { nonNullable: true, validators: Validators.required }),
    mandatory: new FormControl(true, { nonNullable: true }),
  });

  constructor(
    private readonly route: ActivatedRoute,
    private readonly studyService: StudyService,
    private readonly documentRequirementService: DocumentRequirementService,
  ) {}

  ngOnInit(): void {
    this.studyId = Number(this.route.snapshot.paramMap.get('studyId'));
    this.studyService.get(this.studyId).subscribe((s) => this.study.set(s));
    this.load();
  }

  load(): void {
    this.documentRequirementService.listByStudy(this.studyId).subscribe((requirements) => this.requirements.set(requirements));
  }

  openCreateForm(): void {
    this.errorMessage.set(null);
    this.createForm.reset({ studyPhase: 'ACTIVE', documentCategory: '', mandatory: true });
    this.showCreateForm.set(true);
  }

  cancelCreateForm(): void {
    this.showCreateForm.set(false);
  }

  submitCreate(): void {
    if (this.createForm.invalid) {
      return;
    }
    const { studyPhase, documentCategory, mandatory } = this.createForm.getRawValue();
    this.errorMessage.set(null);
    this.documentRequirementService.create({ studyId: this.studyId, studyPhase, documentCategory, mandatory }).subscribe({
      next: () => {
        this.showCreateForm.set(false);
        this.load();
      },
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not create document requirement.'),
    });
  }
}
