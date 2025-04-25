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

        // 生成唯一的correlationId
        const correlationId = this.taskService.generateCorrelationId();

        // 打開不可關閉的進度彈窗
        const dialogRef = this.dialog.open(ProgressDialogComponent, {
            width: '600px',
            disableClose: true,
            data: {
                correlationId,
                items: this.selection
            }
        });

        // 向基金系統發送請求
        if (fundItems.length > 0) {
            const fundRequest = {
                correlationId: correlationId + '-fund',
                taskName: '基金處理',
                numberOfSubtasks: fundItems.length,
                items: fundItems.map(item => item.id)
            };

            this.fundBondService.initiateFundTask(fundRequest).subscribe({
                next: (response) => {
                    console.log('基金請求已發送:', response);
                    // 連接基金SSE
                    this.taskService.connectToEventStream(correlationId + '-fund', 'fund');
                },
                error: (error) => {
                    console.error('基金請求失敗:', error);
                    // 通知彈窗錯誤
                    this.fundBondService.notifyError(correlationId + '-fund', '基金請求失敗');
                }
            });
        }

        // 向債券系統發送請求
        if (bondItems.length > 0) {
            const bondRequest = {
                correlationId: correlationId + '-bond',
                taskName: '債券處理',
                numberOfSubtasks: bondItems.length,
                items: bondItems.map(item => item.id)
            };

            this.fundBondService.initiateBondTask(bondRequest).subscribe({
                next: (response) => {
                    console.log('債券請求已發送:', response);
                    // 連接債券SSE
                    this.taskService.connectToEventStream(correlationId + '-bond', 'bond');
                },
                error: (error) => {
                    console.error('債券請求失敗:', error);
                    // 通知彈窗錯誤
                    this.fundBondService.notifyError(correlationId + '-bond', '債券請求失敗');
                }
            });
        }
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