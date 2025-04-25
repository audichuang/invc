import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { CommonModule, DatePipe } from '@angular/common';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';

// Angular Material 模塊
import { MatTableModule } from '@angular/material/table';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { TaskFormComponent } from './components/task-form/task-form.component';
import { TaskEventsComponent } from './components/task-events/task-events.component';
import { FundBondTableComponent } from './components/fund-bond-table/fund-bond-table.component';
import { ProgressDialogComponent } from './components/progress-dialog/progress-dialog.component';

@NgModule({
    declarations: [
        AppComponent,
        TaskFormComponent,
        TaskEventsComponent,
        FundBondTableComponent,
        ProgressDialogComponent
    ],
    imports: [
        BrowserModule,
        AppRoutingModule,
        HttpClientModule,
        FormsModule,
        ReactiveFormsModule,
        CommonModule,
        BrowserAnimationsModule,
        // Material 模塊
        MatTableModule,
        MatCheckboxModule,
        MatButtonModule,
        MatDialogModule,
        MatProgressBarModule,
        MatProgressSpinnerModule,
        MatIconModule
    ],
    providers: [
        DatePipe
    ],
    bootstrap: [AppComponent]
})
export class AppModule { } 