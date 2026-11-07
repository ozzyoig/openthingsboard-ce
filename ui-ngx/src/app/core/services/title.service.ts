// SPDX-FileCopyrightText: Copyright The Thingsboard Authors
// SPDX-License-Identifier: Apache-2.0
import { Title } from '@angular/platform-browser';
import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { filter } from 'rxjs/operators';
import { combineLatest, of, Subscription } from 'rxjs';
import { BrandingService } from '@core/http/branding.service';

@Injectable({
  providedIn: 'root'
})
export class TitleService {
  private titleSubscription: Subscription;
  constructor(
    private translate: TranslateService,
    private title: Title,
    private branding: BrandingService
  ) {}

  setTitle(
    snapshot: ActivatedRouteSnapshot,
    lazyTranslate?: TranslateService
  ) {
    let lastChild = snapshot;
    while (lastChild.children.length) {
      lastChild = lastChild.children[0];
    }
    const { title } = lastChild.data;
    const translate = lazyTranslate || this.translate;
    this.titleSubscription?.unsubscribe();
    this.titleSubscription = combineLatest([
      this.branding.settings$,
      title ? translate.get(title).pipe(filter(translatedTitle => translatedTitle !== title)) : of('')
    ]).subscribe(([branding, translatedTitle]) => this.title.setTitle(
      translatedTitle ? `${branding.appName} | ${translatedTitle}` : branding.appName
    ));
  }
}
