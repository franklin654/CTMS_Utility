import { Component, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router } from '@angular/router';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs';
import { SiteService } from '../../../core/sites/site.service';
import { StudyResponse, StudyService } from '../../../core/studies/study.service';

@Component({
  selector: 'app-site-create',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatAutocompleteModule],
  templateUrl: './site-create.component.html',
})
export class SiteCreateComponent implements OnInit {
  readonly studySearchControl = new FormControl('', { nonNullable: true });
  readonly studySuggestions = signal<StudyResponse[]>([]);
  readonly errorMessage = signal<string | null>(null);

  readonly form = new FormGroup({
    studyId: new FormControl<number | null>(null, { validators: Validators.required }),
    siteCode: new FormControl('', { nonNullable: true, validators: Validators.required }),
    name: new FormControl('', { nonNullable: true, validators: Validators.required }),
    addressLine1: new FormControl('', { nonNullable: true, validators: Validators.required }),
    addressLine2: new FormControl(''),
    city: new FormControl('', { nonNullable: true, validators: Validators.required }),
    stateProvince: new FormControl(''),
    postalCode: new FormControl(''),
    country: new FormControl('', { nonNullable: true, validators: Validators.required }),
    principalInvestigatorName: new FormControl('', { nonNullable: true, validators: Validators.required }),
    principalInvestigatorContact: new FormControl('', { nonNullable: true, validators: Validators.required }),
    contactName: new FormControl('', { nonNullable: true, validators: Validators.required }),
    contactEmail: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    contactPhone: new FormControl('', { nonNullable: true, validators: Validators.required }),
    feasibilityStatus: new FormControl('', { nonNullable: true, validators: Validators.required }),
    regulatoryInformation: new FormControl(''),
  });

  constructor(
    private readonly siteService: SiteService,
    private readonly studyService: StudyService,
    private readonly route: ActivatedRoute,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    this.studySearchControl.valueChanges
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((value) => this.studyService.list(value.trim(), 0, 20)),
      )
      .subscribe((page) => this.studySuggestions.set(page.content));

    const studyIdParam = this.route.snapshot.queryParamMap.get('studyId');
    if (studyIdParam) {
      const studyId = Number(studyIdParam);
      this.form.patchValue({ studyId });
      this.studyService.get(studyId).subscribe((study) => this.selectStudy(study));
    }
  }

  selectStudy(study: StudyResponse): void {
    this.form.controls.studyId.setValue(study.id);
    // Also drives the visible input text when pre-filled from a ?studyId= query param, where
    // there's no real autocomplete selection event to set it for us.
    this.studySearchControl.setValue(`${study.studyCode} – ${study.name}`, { emitEvent: false });
  }

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.errorMessage.set(null);
    const raw = this.form.getRawValue();
    this.siteService
      .create({
        studyId: raw.studyId!,
        siteCode: raw.siteCode,
        name: raw.name,
        addressLine1: raw.addressLine1,
        addressLine2: raw.addressLine2 || null,
        city: raw.city,
        stateProvince: raw.stateProvince || null,
        postalCode: raw.postalCode || null,
        country: raw.country,
        principalInvestigatorName: raw.principalInvestigatorName,
        principalInvestigatorContact: raw.principalInvestigatorContact,
        contactName: raw.contactName,
        contactEmail: raw.contactEmail,
        contactPhone: raw.contactPhone,
        feasibilityStatus: raw.feasibilityStatus,
        regulatoryInformation: raw.regulatoryInformation || null,
      })
      .subscribe({
        next: (site) => this.router.navigate(['/sites', site.id]),
        error: (err) => this.errorMessage.set(err.error?.message ?? 'Could not register site.'),
      });
  }
}
