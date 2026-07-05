import { Component } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

@Component({
  selector: 'app-payment-hold-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule],
  templateUrl: './payment-hold-dialog.component.html',
})
export class PaymentHoldDialogComponent {
  readonly form = new FormGroup({
    reason: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });

  constructor(private readonly dialogRef: MatDialogRef<PaymentHoldDialogComponent, string>) {}

  cancel(): void {
    this.dialogRef.close();
  }

  confirm(): void {
    if (this.form.invalid) {
      return;
    }
    this.dialogRef.close(this.form.getRawValue().reason);
  }
}
