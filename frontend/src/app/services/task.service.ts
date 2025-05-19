import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject, Subscription, throwError } from 'rxjs';
import { catchError, finalize } from 'rxjs/operators';
import { v4 as uuidv4 } from 'uuid';

/**
 * 任務請求介面
 */
export interface TaskRequest {
    correlationId: string;
    taskName: string;
    numberOfSubtasks: number;
}

/**
 * 系統類型
 */
export type SystemType = 'fund' | 'bond';

/**
 * 任務事件介面
 */
export interface TaskEvent {
    correlationId: string;
    status: string;
    message: string;
    result?: any;
    finalEvent: boolean;
    receivedAt?: Date;
    system?: SystemType;
}

/**
 * SSE 連接配置介面
 */
interface SseConnection {
    abortController: AbortController;
    subscription?: Subscription;
    lastHeartbeatTime: Date;
}

/**
 * 系統 API 配置介面
 */
interface SystemConfig {
    apiUrl: string;
    eventsEndpoint: string;
}

/**
 * 任務服務
 * 負責管理與基金和債券系統的 SSE 連接和事件處理
 */
@Injectable({
    providedIn: 'root'
})
export class TaskService {
    // API 端點配置
    private readonly API_CONFIG: Record<SystemType, SystemConfig> = {
        fund: {
            apiUrl: 'http://localhost:8080/api',
            eventsEndpoint: 'fund-events'
        },
        bond: {
            apiUrl: 'http://localhost:8081/api',
            eventsEndpoint: 'bond-events'
        }
    };

    // 心跳超時時間（毫秒）
    private readonly HEARTBEAT_TIMEOUT_MS = 30000;

    // 事件主題
    private eventSubject = new Subject<TaskEvent>();
    public events$ = this.eventSubject.asObservable();

    // 連接管理
    private connections = new Map<string, SseConnection>();

    constructor(private http: HttpClient) { }

    /**
     * 產生相關 ID
     * @returns 新的 UUID
     */
    public generateCorrelationId(): string {
        return uuidv4();
    }

    /**
     * 發起任務請求 (保留向後兼容)
     * @param request 任務請求
     * @returns 響應 Observable
     */
    public initiateTask(request: TaskRequest): Observable<string> {
        return this.http.post<string>(
            `${this.API_CONFIG.fund.apiUrl}/first-api`,
            request,
            { responseType: 'text' as 'json' }
        ).pipe(
            catchError(error => {
                this.logError('初始化任務失敗', error);
                return throwError(() => error);
            })
        );
    }

    /**
     * 建立 SSE 連接
     * @param correlationId 關聯 ID
     * @param system 系統類型，預設為 'fund'
     * @param taskIds 可選的任務 ID 列表
     */
    public connectToEventStream(correlationId: string, system: SystemType = 'fund', taskIds?: string[]): void {
        // 檢查連接是否已存在
        if (this.connections.has(correlationId)) {
            this.logInfo(`已存在的 SSE 連接 (${correlationId})`);
            return;
        }

        // 獲取系統配置
        const config = this.API_CONFIG[system];

        // 建立連接
        const abortController = new AbortController();
        const connection: SseConnection = {
            abortController,
            lastHeartbeatTime: new Date()
        };

        this.connections.set(correlationId, connection);

        // 建立並訂閱 SSE Observable
        const subscription = this.createSseObservable(correlationId, system, config, abortController, taskIds)
            .subscribe({
                error: (error: Error) => this.handleConnectionError(correlationId, system, error),
                complete: () => this.logInfo(`${system} 系統 SSE 連接已完成 (${correlationId})`)
            });

        // 保存訂閱
        connection.subscription = subscription;
        this.logInfo(`${system} 系統 SSE 連接已建立 (${correlationId})`);
    }

    /**
     * 檢查連接健康狀態
     * @param correlationId 關聯 ID
     * @returns 如果最後一次心跳在超時時間內，返回 true
     */
    public isConnectionHealthy(correlationId: string): boolean {
        const connection = this.connections.get(correlationId);
        if (!connection) return false;

        const now = new Date();
        const diff = now.getTime() - connection.lastHeartbeatTime.getTime();
        return diff < this.HEARTBEAT_TIMEOUT_MS;
    }

    /**
     * 獲取最後一次心跳時間
     * @param correlationId 關聯 ID
     * @returns 最後一次心跳時間，如果不存在則返回 undefined
     */
    public getLastHeartbeatTime(correlationId: string): Date | undefined {
        return this.connections.get(correlationId)?.lastHeartbeatTime;
    }

    /**
     * 關閉特定 SSE 連接
     * @param correlationId 關聯 ID
     */
    public disconnectEventStream(correlationId: string): void {
        const connection = this.connections.get(correlationId);
        if (!connection) return;

        // 中止連接並取消訂閱
        if (!connection.abortController.signal.aborted) {
            connection.abortController.abort();
        }

        if (connection.subscription) {
            connection.subscription.unsubscribe();
        }

        // 移除連接
        this.connections.delete(correlationId);
        this.logInfo(`SSE 連接 ${correlationId} 已關閉並清理`);
    }

    /**
     * 關閉所有 SSE 連接
     */
    public disconnectAllEventStreams(): void {
        this.connections.forEach((connection, id) => {
            if (!connection.abortController.signal.aborted) {
                connection.abortController.abort();
            }

            if (connection.subscription) {
                connection.subscription.unsubscribe();
            }

            this.logInfo(`SSE 連接 ${id} 已關閉`);
        });

        this.connections.clear();
        this.logInfo('所有 SSE 連接已清理');
    }

    /**
     * 建立 SSE Observable
     * @private
     */
    private createSseObservable(
        correlationId: string,
        system: SystemType,
        config: SystemConfig,
        abortController: AbortController,
        taskIds?: string[]
    ): Observable<void> {
        return new Observable<void>(observer => {
            const fetchData = async () => {
                try {
                    // 建立 SSE 連接請求主體
                    const requestBody: any = { correlationId };
                    if (taskIds && taskIds.length > 0) {
                        requestBody.taskIds = taskIds;
                    }

                    // 建立 SSE 連接
                    const response = await fetch(`${config.apiUrl}/${config.eventsEndpoint}`, {
                        method: 'POST',
                        signal: abortController.signal,
                        headers: {
                            'Content-Type': 'application/json',
                            'Accept': 'text/event-stream'
                        },
                        body: JSON.stringify(requestBody)
                    });

                    // 檢查響應
                    if (!response.ok) {
                        throw new Error(`HTTP error! status: ${response.status}`);
                    }

                    if (!response.body) {
                        throw new Error('Response body is null');
                    }

                    this.logInfo(`${system} 系統 SSE 連接已建立 (${correlationId})`);

                    // 讀取並處理流
                    const reader = response.body.getReader();
                    const decoder = new TextDecoder();
                    let buffer = '';

                    // 處理流
                    while (true) {
                        // 檢查是否被中止
                        if (abortController.signal.aborted) {
                            this.logInfo(`${system} 系統 SSE 連接由客戶端中止 (${correlationId})`);
                            observer.complete();
                            return;
                        }

                        // 讀取下一個數據塊
                        const { done, value } = await reader.read();

                        // 檢查流是否結束
                        if (done) {
                            this.logInfo(`${system} 系統 SSE 串流結束 (${correlationId})`);

                            if (!abortController.signal.aborted) {
                                this.processEvent(JSON.stringify({
                                    correlationId,
                                    status: 'STREAM_CLOSED',
                                    message: `${system} 系統 SSE 串流由伺服器關閉`,
                                    finalEvent: true,
                                }), 'STREAM_CLOSED', system);
                            }

                            observer.complete();
                            return;
                        }

                        // 處理接收到的數據
                        buffer += decoder.decode(value, { stream: true });
                        const lines = buffer.split('\n');
                        buffer = lines.pop() || '';

                        // 處理每一行
                        lines.forEach((line, index) => {
                            // 處理數據行
                            if (line.startsWith('data:')) {
                                const jsonData = line.substring(5).trim();
                                if (jsonData) this.processEvent(jsonData, undefined, system);
                            }
                            // 處理事件行
                            else if (line.startsWith('event:')) {
                                const eventName = line.substring(6).trim();
                                const nextLineIndex = index + 1;

                                if (nextLineIndex < lines.length && lines[nextLineIndex].startsWith('data:')) {
                                    const jsonData = lines[nextLineIndex].substring(5).trim();
                                    if (jsonData) this.processEvent(jsonData, eventName, system);
                                }
                            }
                        });
                    }
                } catch (error: any) {
                    // 錯誤處理
                    if (!abortController.signal.aborted) {
                        this.logError(`${system} 系統 SSE 連接錯誤:`, error);
                        observer.error(error);
                    } else {
                        this.logInfo(`${system} 系統 SSE 連接已取消，錯誤被忽略 (${correlationId})`);
                        observer.complete();
                    }
                }
            };

            // 啟動流處理
            fetchData();

            // 清理函數
            return () => {
                if (!abortController.signal.aborted) {
                    abortController.abort();
                }
            };
        }).pipe(
            finalize(() => {
                // 確保資源被清理
                if (this.connections.has(correlationId)) {
                    this.logInfo(`確保 ${correlationId} 的資源被清理`);
                    // 保留連接資訊但標記為已完成，讓 disconnectEventStream 處理
                }
            })
        );
    }

    /**
     * 處理 SSE 事件
     * @private
     */
    private processEvent(eventData: string, eventName?: string, system?: SystemType): void {
        try {
            // 解析事件數據
            const taskEvent: TaskEvent = JSON.parse(eventData);
            taskEvent.receivedAt = new Date();
            taskEvent.system = system;

            // 檢查是否為心跳事件
            if (eventName === 'HEARTBEAT' || taskEvent.status === 'HEARTBEAT') {
                if (taskEvent.correlationId) {
                    const connection = this.connections.get(taskEvent.correlationId);
                    if (connection) {
                        connection.lastHeartbeatTime = new Date();
                    }
                }
                this.logInfo(`收到 ${system} 系統心跳事件`);
                return;
            }

            // 處理一般事件
            this.logInfo(`收到 ${system} 系統 SSE 事件 (${eventName || 'message'})`);
            this.eventSubject.next(taskEvent);

            // 如果是最終事件，關閉連接
            if (taskEvent.finalEvent) {
                this.disconnectEventStream(taskEvent.correlationId);
            }
        } catch (error) {
            // 處理解析錯誤
            this.logError(`解析 ${system} 系統 SSE 事件失敗:`, error);

            // 發送錯誤事件
            if (system) {
                const correlationId = this.extractCorrelationId(eventData) || 'unknown';
                this.eventSubject.next({
                    correlationId,
                    status: 'ERROR',
                    message: `客戶端解析事件失敗: ${eventData}`,
                    finalEvent: true,
                    receivedAt: new Date(),
                    system
                });

                // 關閉連接
                this.disconnectEventStream(correlationId);
            }
        }
    }

    /**
     * 處理連接錯誤
     * @private
     */
    private handleConnectionError(correlationId: string, system: SystemType, error: Error): void {
        this.logError(`${system} 系統 SSE 連接出錯:`, error);

        // 發送錯誤事件
        this.eventSubject.next({
            correlationId,
            status: 'ERROR',
            message: `${system} 系統 SSE 連接出錯: ${error.message}`,
            finalEvent: true,
            receivedAt: new Date(),
            system
        });

        // 關閉連接
        this.disconnectEventStream(correlationId);
    }

    /**
     * 從事件數據中提取 correlationId
     * @private
     */
    private extractCorrelationId(eventData: string): string | null {
        try {
            // 嘗試從JSON中提取correlationId
            const match = eventData.match(/"correlationId"\s*:\s*"([^"]+)"/);
            return match ? match[1] : null;
        } catch {
            return null;
        }
    }

    /**
     * 記錄信息
     * @private
     */
    private logInfo(message: string): void {
        console.log(`[TaskService] ${message}`);
    }

    /**
     * 記錄錯誤
     * @private
     */
    private logError(message: string, error: any): void {
        console.error(`[TaskService] ${message}`, error);
    }
}