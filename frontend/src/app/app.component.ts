import { Component } from '@angular/core';

@Component({
    selector: 'app-root',
    template: `
    <div class="container mt-4">
      <div class="row">
        <div class="col-12">
          <div class="card">
            <div class="card-header bg-primary text-white">
              <h1 class="mb-0">SSE搭配Kafka</h1>
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
    title = 'SSE搭配Kafka';
} 