import { Component } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface PaymentReleaseResult {
  password: string;
  reason: string;
}

/** Password + reason e-signature dialog for releasing a held payment -- mirrors
 * document-approve-dialog's exact structure, since both are 21 CFR Part 11 sign-off actions. */
@Component({
  selector: 'app-payment-release-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule],
  templateUrl: './payment-release-dialog.component.html',
})
export class PaymentReleaseDialogComponent {
  readonly form = new FormGroup({
    password: new FormControl('', { nonNullable: true, validators: Validators.required }),
    reason: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  constructor(private readonly dialogRef: MatDialogRef<PaymentReleaseDialogComponent, PaymentReleaseResult>) {}

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
