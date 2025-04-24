import { Component } from '@angular/core';

@Component({
    selector: 'app-root',
    template: `
    <div class="container mt-4">
      <div class="row">
        <div class="col-12">
          <div class="card">
            <div class="card-header bg-primary text-white">
              <h1 class="mb-0">非同步任務處理與 SSE 示範</h1>
            </div>
            <div class="card-body">
              <router-outlet></router-outlet>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
    styles: []
})
export class AppComponent {
    title = '非同步任務處理與 SSE 示範';
} 