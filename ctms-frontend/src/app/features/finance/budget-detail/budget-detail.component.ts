import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute } from '@angular/router';
import { BudgetLineItemRequest, BudgetService, BudgetVersionResponse } from '../../../core/budgets/budget.service';
import { StudyResponse, StudyService } from '../../../core/studies/study.service';

function lineItemGroup(): FormGroup {
  return new FormGroup({
    costCategory: new FormControl<string>('MONITORING', { nonNullable: true }),
    plannedAmount: new FormControl<number | null>(null, { validators: Validators.required }),
    currency: new FormControl('USD', { nonNullable: true, validators: Validators.required }),
  });
}

@Component({
  selector: 'app-budget-detail',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatCheckboxModule, DatePipe, DecimalPipe],
  templateUrl: './budget-detail.component.html',
})
export class BudgetDetailComponent implements OnInit {
  readonly study = signal<StudyResponse | null>(null);
  readonly currentVersion = signal<BudgetVersionResponse | null>(null);
  readonly versions = signal<BudgetVersionResponse[]>([]);
  readonly budgetExists = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly showNewVersionForm = signal(false);
  readonly compareSelection = signal<number[]>([]);

  readonly lineItemsArray = new FormArray([lineItemGroup()]);
  readonly reasonControl = new FormControl('', { nonNullable: true, validators: Validators.required });

  private studyId!: number;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly studyService: StudyService,
    private readonly budgetService: BudgetService,
  ) {}

  ngOnInit(): void {
    this.studyId = Number(this.route.snapshot.paramMap.get('studyId'));
    this.studyService.get(this.studyId).subscribe((s) => this.study.set(s));
    this.load();
  }

  load(): void {
    this.errorMessage.set(null);
    this.budgetService.getCurrentVersion(this.studyId).subscribe({
      next: (v) => {
        this.budgetExists.set(true);
        this.currentVersion.set(v);
        this.loadVersions();
      },
      error: (err) => {
        if (err.status === 404) {
          this.budgetExists.set(false);
        } else {
          this.errorMessage.set(err.error?.message ?? 'Could not load budget.');
        }
      },
    });
  }

  loadVersions(): void {
    this.budgetService.listVersions(this.studyId).subscribe((versions) => this.versions.set(versions));
  }

  addLineItem(): void {
    this.lineItemsArray.push(lineItemGroup());
  }

  removeLineItem(index: number): void {
    this.lineItemsArray.removeAt(index);
  }

  private lineItemRequests(): BudgetLineItemRequest[] {
    return this.lineItemsArray.controls.map((group) => ({
      costCategory: group.get('costCategory')!.value,
      plannedAmount: group.get('plannedAmount')!.value,
      currency: group.get('currency')!.value,
    }));
  }

  openNewVersionForm(): void {
    this.errorMessage.set(null);
    this.lineItemsArray.clear();
    const current = this.currentVersion();
    if (current) {
      for (const item of current.lineItems) {
        const group = lineItemGroup();
        group.setValue({ costCategory: item.costCategory, plannedAmount: item.plannedAmount, currency: item.currency });
        this.lineItemsArray.push(group);
      }
    } else {
      this.lineItemsArray.push(lineItemGroup());
    }
    this.reasonControl.reset('');
    this.showNewVersionForm.set(true);
  }

  cancelNewVersionForm(): void {
    this.showNewVersionForm.set(false);
  }

  submitCreateBudget(): void {
    if (this.lineItemsArray.invalid) {
      return;
    }
    this.errorMessage.set(null);
    this.budgetService.create({ studyId: this.studyId, lineItems: this.lineItemRequests() }).subscribe({
      next: () => this.load(),
      error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not create budget.'),
    });
  }

  submitNewVersion(): void {
    if (this.lineItemsArray.invalid || this.reasonControl.invalid) {
      return;
    }
    this.errorMessage.set(null);
    this.budgetService
      .createNewVersion(this.studyId, { lineItems: this.lineItemRequests(), reason: this.reasonControl.value })
      .subscribe({
        next: () => {
          this.showNewVersionForm.set(false);
          this.load();
        },
        error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not create new budget version.'),
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

  compareVersions(): BudgetVersionResponse[] {
    const selection = this.compareSelection();
    return this.versions().filter((v) => selection.includes(v.id));
  }

  export(format: 'pdf' | 'excel'): void {
    this.budgetService.export(this.studyId, format).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = `budget-report.${format === 'excel' ? 'xlsx' : 'pdf'}`;
      anchor.click();
      URL.revokeObjectURL(url);
    });
  }
}
