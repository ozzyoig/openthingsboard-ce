// SPDX-FileCopyrightText: Copyright The Thingsboard Authors
// SPDX-License-Identifier: Apache-2.0
import { Component, OnInit } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { getCurrentAuthState } from '@core/auth/auth.selectors';
import { Authority } from '@shared/models/authority.enum';
import { BrandingService, BrandingSettings } from '@core/http/branding.service';
import { finalize } from 'rxjs/operators';
import { HasConfirmForm } from '@core/guards/confirm-on-exit.guard';

@Component({
  selector: 'tb-branding-settings',
  templateUrl: './branding.component.html',
  styleUrls: ['./settings-card.scss', './branding.component.scss'],
  standalone: false
})
export class BrandingComponent implements OnInit, HasConfirmForm {
  readonly global: boolean;
  readonly imageFields: Array<{key: 'logo' | 'loginLogo' | 'favicon'; label: string}>;
  readonly form = this.fb.group({
    appName: ['', Validators.maxLength(80)],
    primaryColor: ['', Validators.pattern(/^#[0-9a-fA-F]{6}$/)],
    logo: [''], loginLogo: [''], favicon: ['']
  });
  busy = false;
  message = '';
  error = '';

  constructor(private fb: FormBuilder, public branding: BrandingService, store: Store<AppState>) {
    this.global = getCurrentAuthState(store).authUser.authority === Authority.SYS_ADMIN;
    this.imageFields = [{key: 'logo', label: 'branding.logo'},
      ...(this.global ? [{key: 'loginLogo' as const, label: 'branding.login-logo'}] : []),
      {key: 'favicon', label: 'branding.favicon'}];
  }

  ngOnInit() { this.load(); }
  confirmForm() { return this.form; }

  save() {
    if (this.form.invalid || this.busy) { return; }
    const settings: BrandingSettings = {};
    for (const [key, value] of Object.entries(this.form.getRawValue())) {
      settings[key] = value?.trim() || null;
    }
    this.busy = true;
    this.error = '';
    this.branding.save(settings).pipe(finalize(() => this.busy = false)).subscribe({
      next: () => { this.form.markAsPristine(); this.message = 'branding.saved'; this.branding.refresh(); },
      error: () => this.error = 'branding.save-error'
    });
  }

  reset() {
    if (this.busy) { return; }
    this.busy = true;
    this.error = '';
    this.branding.reset().pipe(finalize(() => this.busy = false)).subscribe({
      next: () => { this.load(); this.branding.refresh(); this.message = 'branding.reset-done'; },
      error: () => this.error = 'branding.save-error'
    });
  }

  upload(event: Event, key: 'logo' | 'loginLogo' | 'favicon') {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) { return; }
    this.error = '';
    if (file.size > 256 * 1024 || !(key === 'favicon' ? ['image/png'] : ['image/png', 'image/jpeg', 'image/webp']).includes(file.type)) {
      this.error = 'branding.image-error'; return;
    }
    this.busy = true;
    this.branding.upload(file, key === 'favicon').pipe(finalize(() => this.busy = false)).subscribe({
      next: result => { this.form.controls[key].setValue(result.data); this.form.markAsDirty(); },
      error: () => this.error = 'branding.image-error'
    });
  }

  clear(key: 'logo' | 'loginLogo' | 'favicon') {
    this.form.controls[key].setValue(''); this.form.markAsDirty();
  }

  private load() {
    this.branding.own().subscribe({
      next: settings => this.form.reset(settings),
      error: () => this.error = 'branding.load-error'
    });
  }
}
