import { Component, OnInit } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { MatCheckboxChange } from '@angular/material/checkbox';
import { TaskService } from '../../services/task.service';
import { FundBondService } from '../../services/fund-bond.service';
import { ProgressDialogComponent } from '../progress-dialog/progress-dialog.component';
import { MatDialog } from '@angular/material/dialog';

export interface FundBondItem {
    id: string;
    name: string;
    type: 'fund' | 'bond';
    code: string;
    selected: boolean;
}

@Component({
    selector: 'app-fund-bond-table',
    templateUrl: './fund-bond-table.component.html',
    styleUrls: ['./fund-bond-table.component.css']
})
export class FundBondTableComponent implements OnInit {
    displayedColumns: string[] = ['select', 'name', 'type', 'code'];
    dataSource = new MatTableDataSource<FundBondItem>([]);
    selection: FundBondItem[] = [];

    constructor(
        private taskService: TaskService,
        private fundBondService: FundBondService,
        private dialog: MatDialog
    ) { }

    ngOnInit(): void {
        this.loadItems();
    }

    loadItems(): void {
        const mockData: FundBondItem[] = [
            { id: '1', name: '成長型基金A', type: 'fund', code: 'FUND001', selected: false },
            { id: '2', name: '穩健型基金B', type: 'fund', code: 'FUND002', selected: false },
            { id: '3', name: '高收益債券A', type: 'bond', code: 'BOND001', selected: false },
            { id: '4', name: '政府債券B', type: 'bond', code: 'BOND002', selected: false },
            { id: '5', name: '企業債券C', type: 'bond', code: 'BOND003', selected: false },
            { id: '6', name: '環球基金C', type: 'fund', code: 'FUND003', selected: false },
        ];

        this.dataSource.data = mockData;
    }

    toggleSelection(item: FundBondItem, event: MatCheckboxChange): void {
        item.selected = event.checked;
        this.updateSelection();
    }

    updateSelection(): void {
        this.selection = this.dataSource.data.filter(item => item.selected);
    }

    submitSelection(): void {
        if (this.selection.length === 0) {
            alert('請至少選擇一項');
            return;
        }

        // 分組選擇的項目
        const fundItems = this.selection.filter(item => item.type === 'fund');
        const bondItems = this.selection.filter(item => item.type === 'bond');

        // 生成唯一的correlationId (作為整個處理流程的ID)
        const correlationId = this.taskService.generateCorrelationId();
        const fundSseId = correlationId + '-fund';
        const bondSseId = correlationId + '-bond';

        // 打開不可關閉的進度彈窗
        const dialogRef = this.dialog.open(ProgressDialogComponent, {
            width: '800px',
            maxWidth: '95vw',
            disableClose: true,
            autoFocus: false,
            restoreFocus: false,
            maxHeight: '90vh',
            panelClass: ['centered-dialog', 'mat-dialog-center-position'],
            data: {
                correlationId,
                items: this.selection
            }
        });

        // 先為每種類型建立SSE連接（如果有相應類型的項目）
        if (fundItems.length > 0) {
            this.taskService.connectToEventStream(fundSseId, 'fund');
            console.log('建立基金SSE連接:', fundSseId);
        }

        if (bondItems.length > 0) {
            this.taskService.connectToEventStream(bondSseId, 'bond');
            console.log('建立債券SSE連接:', bondSseId);
        }

        // 對於基金項目，每個項目分別發送請求
        fundItems.forEach((item, index) => {
            const itemId = `${correlationId}-fund-${index}`;
            const fundRequest = {
                correlationId: itemId,
                taskName: `基金處理-${item.name}`,
                numberOfSubtasks: 1,
                items: [item.id]
            };

            this.fundBondService.initiateFundTask(fundRequest).subscribe({
                next: (response: string) => {
                    console.log(`基金${item.name}請求已發送:`, response);
                },
                error: (error: any) => {
                    console.error(`基金${item.name}請求失敗:`, error);
                    this.fundBondService.notifyError(itemId, `基金${item.name}請求失敗`);
                }
            });
        });

        // 對於債券項目，每個項目分別發送請求
        bondItems.forEach((item, index) => {
            const itemId = `${correlationId}-bond-${index}`;
            const bondRequest = {
                correlationId: itemId,
                taskName: `債券處理-${item.name}`,
                numberOfSubtasks: 1,
                items: [item.id]
            };

            this.fundBondService.initiateBondTask(bondRequest).subscribe({
                next: (response: string) => {
                    console.log(`債券${item.name}請求已發送:`, response);
                },
                error: (error: any) => {
                    console.error(`債券${item.name}請求失敗:`, error);
                    this.fundBondService.notifyError(itemId, `債券${item.name}請求失敗`);
                }
            });
        });

        // 監聽對話框關閉事件，確保在對話框關閉時斷開所有SSE連接
        dialogRef.afterClosed().subscribe(() => {
            console.log('對話框已關閉，斷開SSE連接');
            if (fundItems.length > 0) {
                this.taskService.disconnectEventStream(fundSseId);
            }
            if (bondItems.length > 0) {
                this.taskService.disconnectEventStream(bondSseId);
            }
        });
    }

    selectAll(event: MatCheckboxChange): void {
        this.dataSource.data.forEach(item => item.selected = event.checked);
        this.updateSelection();
    }

    isAllSelected(): boolean {
        return this.dataSource.data.length > 0 &&
            this.dataSource.data.every(item => item.selected);
    }

    isSomeSelected(): boolean {
        return this.dataSource.data.some(item => item.selected) &&
            !this.isAllSelected();
    }
} 