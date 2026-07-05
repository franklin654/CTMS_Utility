import { Component, Inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface DocumentReviewDialogData {
  action: 'APPROVED' | 'REJECTED' | 'CHANGES_REQUESTED';
}

/** Plain comment dialog for reviewer decisions and approval-stage rejection -- comment is
 * mandatory unless action is APPROVED (matches the backend's cross-field rule). */
@Component({
  selector: 'app-document-review-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule],
  templateUrl: './document-review-dialog.component.html',
})
export class DocumentReviewDialogComponent {
  readonly commentRequired: boolean;
  readonly comment: FormControl<string>;

  constructor(
    private readonly dialogRef: MatDialogRef<DocumentReviewDialogComponent, string>,
    @Inject(MAT_DIALOG_DATA) readonly data: DocumentReviewDialogData,
  ) {
    this.commentRequired = data.action !== 'APPROVED';
    this.comment = new FormControl('', {
      nonNullable: true,
      validators: this.commentRequired ? Validators.required : [],
    });
  }

  cancel(): void {
    this.dialogRef.close();
  }

  confirm(): void {
    if (this.comment.invalid) {
      return;
    }
    this.dialogRef.close(this.comment.value);
  }
}
