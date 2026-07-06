import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Page, PaymentResponse, PaymentService } from '../../../core/payments/payment.service';
import { StatusChipPipe } from '../../../core/utils/status-chip.pipe';
import { PaymentHoldDialogComponent } from '../payment-hold-dialog/payment-hold-dialog.component';
import { PaymentReleaseDialogComponent, PaymentReleaseResult } from '../payment-release-dialog/payment-release-dialog.component';

@Component({
  selector: 'app-payment-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDialogModule,
    DatePipe,
    DecimalPipe,
    StatusChipPipe,
  ],
  templateUrl: './payment-list.component.html',
})
export class PaymentListComponent implements OnInit {
  readonly page = signal<Page<PaymentResponse> | null>(null);
  readonly errorMessage = signal<string | null>(null);

  readonly filterForm = new FormGroup({
    studyId: new FormControl<number | null>(null),
    siteId: new FormControl<number | null>(null),
    costCategory: new FormControl<string | null>(null),
    status: new FormControl<string | null>(null),
  });

  constructor(
    private readonly paymentService: PaymentService,
    private readonly dialog: MatDialog,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const raw = this.filterForm.getRawValue();
    this.errorMessage.set(null);
    this.paymentService
      .list({ studyId: raw.studyId, siteId: raw.siteId, costCategory: raw.costCategory, status: raw.status })
      .subscribe({
        next: (p) => this.page.set(p),
        error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not load payments.'),
      });
  }

  applyFilters(): void {
    this.load();
  }

  openHoldDialog(payment: PaymentResponse): void {
    const dialogRef = this.dialog.open(PaymentHoldDialogComponent, { width: '480px' });
    dialogRef.afterClosed().subscribe((reason: string | undefined) => {
      if (!reason) {
        return;
      }
      this.errorMessage.set(null);
      this.paymentService.hold(payment.id, reason).subscribe({
        next: () => this.load(),
        error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not hold payment.'),
      });
    });
  }

  openReleaseDialog(payment: PaymentResponse): void {
    const dialogRef = this.dialog.open(PaymentReleaseDialogComponent, { width: '480px' });
    dialogRef.afterClosed().subscribe((result: PaymentReleaseResult | undefined) => {
      if (!result) {
        return;
      }
      this.errorMessage.set(null);
      this.paymentService.release(payment.id, result.reason, result.password).subscribe({
        next: () => this.load(),
        error: (err) => {
          const message = err.status === 401 ? 'Incorrect password. Please try again.' : (err.error?.message ?? 'Release failed.');
          this.errorMessage.set(message);
        },
      });
    });
  }
}
