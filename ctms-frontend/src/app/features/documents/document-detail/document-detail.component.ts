import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { ActivatedRoute } from '@angular/router';
import { HasRoleDirective } from '../../../core/auth/has-role.directive';
import {
  DocumentResponse,
  DocumentService,
  DocumentVersionResponse,
} from '../../../core/documents/document.service';
import { StatusChipPipe } from '../../../core/utils/status-chip.pipe';
import { DocumentApproveDialogComponent } from '../document-approve-dialog/document-approve-dialog.component';
import { DocumentReviewDialogComponent } from '../document-review-dialog/document-review-dialog.component';

@Component({
  selector: 'app-document-detail',
  standalone: true,
  imports: [MatButtonModule, MatDialogModule, DatePipe, HasRoleDirective, StatusChipPipe],
  templateUrl: './document-detail.component.html',
})
export class DocumentDetailComponent implements OnInit {
  readonly document = signal<DocumentResponse | null>(null);
  readonly versions = signal<DocumentVersionResponse[]>([]);
  readonly errorMessage = signal<string | null>(null);
  readonly compareSelection = signal<number[]>([]);
  readonly selectedFile = signal<File | null>(null);

  private documentId!: number;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly documentService: DocumentService,
    private readonly dialog: MatDialog,
  ) {}

  ngOnInit(): void {
    this.documentId = Number(this.route.snapshot.paramMap.get('id'));
    this.load();
  }

  load(): void {
    this.documentService.get(this.documentId).subscribe((doc) => this.document.set(doc));
    this.documentService.versions(this.documentId).subscribe((v) => this.versions.set(v));
  }

  download(version: DocumentVersionResponse): void {
    this.documentService.download(this.documentId, version.versionNumber).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = version.fileName;
      anchor.click();
      URL.revokeObjectURL(url);
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.[0] ?? null);
  }

  addVersion(): void {
    const file = this.selectedFile();
    if (!file) {
      return;
    }
    this.errorMessage.set(null);
    this.documentService.addVersion(this.documentId, file).subscribe({
      next: () => {
        this.selectedFile.set(null);
        this.load();
      },
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not add version.'),
    });
  }

  submitForReview(versionNumber: number): void {
    this.errorMessage.set(null);
    this.documentService.submitForReview(this.documentId, versionNumber).subscribe({
      next: () => this.load(),
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not submit for review.'),
    });
  }

  reviewerDecide(versionNumber: number, action: 'APPROVED' | 'REJECTED' | 'CHANGES_REQUESTED'): void {
    const dialogRef = this.dialog.open(DocumentReviewDialogComponent, { data: { action }, width: '480px' });
    dialogRef.afterClosed().subscribe((comment: string | undefined) => {
      if (comment === undefined) {
        return;
      }
      this.errorMessage.set(null);
      this.documentService.review(this.documentId, versionNumber, action, comment || null).subscribe({
        next: () => this.load(),
        error: (err) => this.errorMessage.set(err.error?.message ?? 'Review action failed.'),
      });
    });
  }

  finalApprove(versionNumber: number): void {
    const dialogRef = this.dialog.open(DocumentApproveDialogComponent, { width: '480px' });
    dialogRef.afterClosed().subscribe((result) => {
      if (!result) {
        return;
      }
      this.errorMessage.set(null);
      this.documentService
        .approve(this.documentId, versionNumber, 'APPROVED', null, result.password, result.reason)
        .subscribe({
          next: () => this.load(),
          error: (err) => {
            const message = err.status === 401 ? 'Incorrect password. Please try again.' : (err.error?.message ?? 'Approval failed.');
            this.errorMessage.set(message);
          },
        });
    });
  }

  finalReject(versionNumber: number): void {
    const dialogRef = this.dialog.open(DocumentReviewDialogComponent, { data: { action: 'REJECTED' }, width: '480px' });
    dialogRef.afterClosed().subscribe((comment: string | undefined) => {
      if (comment === undefined) {
        return;
      }
      this.errorMessage.set(null);
      this.documentService.approve(this.documentId, versionNumber, 'REJECTED', comment, null, null).subscribe({
        next: () => this.load(),
        error: (err) => this.errorMessage.set(err.error?.message ?? 'Rejection failed.'),
      });
    });
  }

  toggleCompare(versionId: number): void {
    this.compareSelection.update((selection) => {
      if (selection.includes(versionId)) {
        return selection.filter((id) => id !== versionId);
      }
      if (selection.length >= 2) {
        return [selection[1], versionId];
      }
      return [...selection, versionId];
    });
  }

  compareVersions(): DocumentVersionResponse[] {
    const selection = this.compareSelection();
    return this.versions().filter((v) => selection.includes(v.id));
  }
}
