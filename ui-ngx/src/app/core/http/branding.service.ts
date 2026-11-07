// SPDX-FileCopyrightText: Copyright The Thingsboard Authors
// SPDX-License-Identifier: Apache-2.0
import { DOCUMENT } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Inject, Injectable } from '@angular/core';
import { Store } from '@ngrx/store';
import { BehaviorSubject, combineLatest, of } from 'rxjs';
import { catchError, distinctUntilChanged, map, switchMap } from 'rxjs/operators';
import { AppState } from '@core/core.state';
import { selectAuthUser } from '@core/auth/auth.selectors';
import { Authority } from '@shared/models/authority.enum';

export interface BrandingSettings {
  appName?: string;
  logo?: string;
  loginLogo?: string;
  favicon?: string;
  primaryColor?: string;
}

@Injectable({providedIn: 'root'})
export class BrandingService {
  readonly settings$ = new BehaviorSubject<BrandingSettings>({appName: 'OpenThingsBoard CE'});
  private authenticated = false;
  private readonly reload$ = new BehaviorSubject(0);

  constructor(private http: HttpClient, store: Store<AppState>, @Inject(DOCUMENT) private document: Document) {
    combineLatest([
      store.select(selectAuthUser).pipe(
        map(user => user && [Authority.SYS_ADMIN, Authority.TENANT_ADMIN, Authority.CUSTOMER_USER].includes(user.authority)
          ? `${user.authority}:${user.tenantId}` : ''),
        distinctUntilChanged()
      ),
      this.reload$
    ]).pipe(
      switchMap(([scope]) => {
        this.authenticated = !!scope;
        // Drop the previous tenant's branding immediately, including on logout.
        this.apply({appName: 'OpenThingsBoard CE'});
        return this.load();
      })
    ).subscribe(settings => this.apply(settings));
  }

  get value(): BrandingSettings { return this.settings$.value; }

  own() { return this.http.get<BrandingSettings>('/api/branding'); }
  save(settings: BrandingSettings) { return this.http.post<BrandingSettings>('/api/branding', settings); }
  reset() { return this.http.delete<void>('/api/branding'); }
  refresh() { this.reload$.next(this.reload$.value + 1); }

  upload(file: File, favicon: boolean) {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<{data: string}>('/api/branding/image', body, {params: {favicon}});
  }

  private load() {
    return this.http.get<BrandingSettings>(this.authenticated ? '/api/branding/effective' : '/api/noauth/branding').pipe(
      catchError(() => of({appName: 'OpenThingsBoard CE'}))
    );
  }

  private apply(settings: BrandingSettings) {
    this.settings$.next(settings);
    const icon = this.document.querySelector<HTMLLinkElement>('link[rel="icon"]');
    if (icon) {
      icon.href = settings.favicon || 'thingsboard.ico';
      icon.type = settings.favicon ? 'image/png' : 'image/x-icon';
    }
    const root = this.document.documentElement;
    root.style.removeProperty('--tb-brand-primary');
    root.style.removeProperty('--tb-brand-on-primary');
    root.classList.toggle('tb-branded', !!settings.primaryColor);
    if (/^#[0-9a-fA-F]{6}$/.test(settings.primaryColor || '')) {
      root.style.setProperty('--tb-brand-primary', settings.primaryColor);
      const rgb = [1, 3, 5].map(i => parseInt(settings.primaryColor.slice(i, i + 2), 16) / 255)
        .map(c => c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
      const luminance = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
      root.style.setProperty('--tb-brand-on-primary', luminance > 0.179 ? '#000000' : '#ffffff');
    }
  }
}
