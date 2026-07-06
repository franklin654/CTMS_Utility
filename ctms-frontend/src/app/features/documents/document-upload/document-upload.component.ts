import { Component, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router } from '@angular/router';
import { DocumentService } from '../../../core/documents/document.service';
import { StudyResponse, StudyService } from '../../../core/studies/study.service';

export const DOCUMENT_CATEGORIES = [
  'PROTOCOL',
  'INFORMED_CONSENT',
  'PRINCIPAL_INVESTIGATOR_CV',
  'REGULATORY_APPROVAL',
  'FINANCIAL',
  'MONITORING_REPORT',
  'SOP',
  'OTHER',
];

@Component({
  selector: 'app-document-upload',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule],
  templateUrl: './document-upload.component.html',
})
export class DocumentUploadComponent implements OnInit {
  readonly categories = DOCUMENT_CATEGORIES;
  readonly studies = signal<StudyResponse[]>([]);
  readonly selectedFile = signal<File | null>(null);
  readonly errorMessage = signal<string | null>(null);

  readonly form = new FormGroup({
    title: new FormControl('', { nonNullable: true, validators: Validators.required }),
    category: new FormControl('', { nonNullable: true, validators: Validators.required }),
    studyId: new FormControl<number | null>(null),
  });

  constructor(
    private readonly documentService: DocumentService,
    private readonly studyService: StudyService,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    this.studyService.list(undefined, 0, 100).subscribe((page) => this.studies.set(page.content));
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.[0] ?? null);
  }

  submit(): void {
    if (this.form.invalid || !this.selectedFile()) {
      return;
    }
    this.errorMessage.set(null);
    const { title, category, studyId } = this.form.getRawValue();
    this.documentService.create(title, category, studyId, null, this.selectedFile()!).subscribe({
      next: (doc) => this.router.navigate(['/documents', doc.id]),
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not upload document.'),
    });
  }
}
