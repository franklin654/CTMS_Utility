import { Component } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface SiteActivationResult {
  password: string;
  reason: string;
}

/** Password + reason e-signature dialog for activating a site -- mirrors
 * payment-release-dialog's exact structure, since both are 21 CFR Part 11 sign-off actions. */
@Component({
  selector: 'app-site-activation-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule],
  templateUrl: './site-activation-dialog.component.html',
})
export class SiteActivationDialogComponent {
  readonly form = new FormGroup({
    password: new FormControl('', { nonNullable: true, validators: Validators.required }),
    reason: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  constructor(private readonly dialogRef: MatDialogRef<SiteActivationDialogComponent, SiteActivationResult>) {}

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
