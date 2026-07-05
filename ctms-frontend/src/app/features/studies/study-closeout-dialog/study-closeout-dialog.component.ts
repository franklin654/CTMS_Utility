import { Component } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface StudyCloseoutResult {
  password: string;
  reason: string;
}

@Component({
  selector: 'app-study-closeout-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule],
  templateUrl: './study-closeout-dialog.component.html',
})
export class StudyCloseoutDialogComponent {
  readonly form = new FormGroup({
    password: new FormControl('', { nonNullable: true, validators: Validators.required }),
    reason: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  constructor(private readonly dialogRef: MatDialogRef<StudyCloseoutDialogComponent, StudyCloseoutResult>) {}

  cancel(): void {
    this.dialogRef.close();
  }

  confirm(): void {
    if (this.form.invalid) {
      return;
    }
    this.dialogRef.close(this.form.getRawValue());
  }
}
