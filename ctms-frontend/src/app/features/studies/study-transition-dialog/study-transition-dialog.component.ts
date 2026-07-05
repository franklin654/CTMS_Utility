import { Component, Inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface StudyTransitionDialogData {
  targetStatus: string;
}

@Component({
  selector: 'app-study-transition-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule],
  templateUrl: './study-transition-dialog.component.html',
})
export class StudyTransitionDialogComponent {
  readonly justification = new FormControl('', { nonNullable: true, validators: Validators.required });

  constructor(
    private readonly dialogRef: MatDialogRef<StudyTransitionDialogComponent, string>,
    @Inject(MAT_DIALOG_DATA) readonly data: StudyTransitionDialogData,
  ) {}

  cancel(): void {
    this.dialogRef.close();
  }

  confirm(): void {
    if (this.justification.invalid) {
      return;
    }
    this.dialogRef.close(this.justification.value);
  }
}
