import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { DocumentResponse } from '../../../core/documents/document.service';
import { PatientDocumentService } from '../../../core/patient/patient-document.service';

@Component({
  selector: 'app-patient-documents',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, DatePipe],
  templateUrl: './patient-documents.component.html',
})
export class PatientDocumentsComponent implements OnInit {
  readonly documents = signal<DocumentResponse[]>([]);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  selectedFile: File | null = null;

  readonly uploadForm = new FormGroup({
    category: new FormControl('', { nonNullable: true, validators: Validators.required }),
    title: new FormControl('', { nonNullable: true, validators: Validators.required }),
    effectiveDate: new FormControl('', { nonNullable: true }),
  });

  constructor(private readonly patientDocumentService: PatientDocumentService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.patientDocumentService.list().subscribe({
      next: (page) => this.documents.set(page.content),
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not load your documents.'),
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile = input.files?.[0] ?? null;
  }

  submitUpload(): void {
    if (this.uploadForm.invalid || !this.selectedFile) {
      this.errorMessage.set('Please choose a file and fill in the required fields.');
      return;
    }
    this.errorMessage.set(null);
    this.successMessage.set(null);
    const { category, title, effectiveDate } = this.uploadForm.getRawValue();
    this.patientDocumentService.upload(category, title, effectiveDate || null, this.selectedFile).subscribe({
      next: () => {
        this.successMessage.set('Document uploaded -- it will be reviewed by the study team before appearing as current.');
        this.uploadForm.reset({ category: '', title: '', effectiveDate: '' });
        this.selectedFile = null;
        this.load();
      },
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not upload document.'),
    });
  }
}
