import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { TaskFormComponent } from './components/task-form/task-form.component';
import { FundBondTableComponent } from './components/fund-bond-table/fund-bond-table.component';

const routes: Routes = [
    { path: '', redirectTo: '/fund-bond', pathMatch: 'full' },
    { path: 'task-form', component: TaskFormComponent },
    { path: 'fund-bond', component: FundBondTableComponent }
];

@NgModule({
    imports: [RouterModule.forRoot(routes)],
    exports: [RouterModule]
})
export class AppRoutingModule { } 