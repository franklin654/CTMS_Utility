import { Component, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';
import { DocumentService, DocumentVersionResponse } from '../../../core/documents/document.service';
import { DocumentApproveDialogComponent } from '../document-approve-dialog/document-approve-dialog.component';
import { DocumentReviewDialogComponent } from '../document-review-dialog/document-review-dialog.component';

@Component({
  selector: 'app-document-approval-queue',
  standalone: true,
  imports: [MatButtonModule, MatDialogModule, MatTabsModule, RouterLink],
  templateUrl: './document-approval-queue.component.html',
})
export class DocumentApprovalQueueComponent implements OnInit {
  readonly reviewQueue = signal<DocumentVersionResponse[]>([]);
  readonly approvalQueueList = signal<DocumentVersionResponse[]>([]);
  readonly errorMessage = signal<string | null>(null);

  constructor(
    private readonly documentService: DocumentService,
    private readonly dialog: MatDialog,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.documentService.approvalQueue('REVIEW').subscribe((page) => this.reviewQueue.set(page.content));
    this.documentService.approvalQueue('APPROVAL').subscribe((page) => this.approvalQueueList.set(page.content));
  }

  reviewerDecide(version: DocumentVersionResponse, action: 'APPROVED' | 'REJECTED' | 'CHANGES_REQUESTED'): void {
    const dialogRef = this.dialog.open(DocumentReviewDialogComponent, { data: { action } });
    dialogRef.afterClosed().subscribe((comment: string | undefined) => {
      if (comment === undefined) {
        return;
      }
      this.errorMessage.set(null);
      this.documentService.review(version.documentId, version.versionNumber, action, comment || null).subscribe({
        next: () => this.load(),
        error: (err) => this.errorMessage.set(err.error?.message ?? 'Review action failed.'),
      });
    });
  }

  finalApprove(version: DocumentVersionResponse): void {
    const dialogRef = this.dialog.open(DocumentApproveDialogComponent);
    dialogRef.afterClosed().subscribe((result) => {
      if (!result) {
        return;
      }
      this.errorMessage.set(null);
      this.documentService
        .approve(version.documentId, version.versionNumber, 'APPROVED', null, result.password, result.reason)
        .subscribe({
          next: () => this.load(),
          error: (err) => {
            const message = err.status === 401 ? 'Incorrect password. Please try again.' : (err.error?.message ?? 'Approval failed.');
            this.errorMessage.set(message);
          },
        });
    });
  }

  finalReject(version: DocumentVersionResponse): void {
    const dialogRef = this.dialog.open(DocumentReviewDialogComponent, { data: { action: 'REJECTED' } });
    dialogRef.afterClosed().subscribe((comment: string | undefined) => {
      if (comment === undefined) {
        return;
      }
      this.errorMessage.set(null);
      this.documentService.approve(version.documentId, version.versionNumber, 'REJECTED', comment, null, null).subscribe({
        next: () => this.load(),
        error: (err) => this.errorMessage.set(err.error?.message ?? 'Rejection failed.'),
      });
    });
  }
}
