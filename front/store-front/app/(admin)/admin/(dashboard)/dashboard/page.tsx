import SalesOverview from "@/components/admin/dashboard/SalesOverview"; 
import { YearlyBreakup } from "@/components/admin/dashboard/YearlyBreakup";


export default function DashboardPage() {
    return (
        <div className="space-y-6">
            <h2 className="text-3xl font-bold tracking-tight text-gray-800">대시보드</h2>

            {/* 템플릿의 위젯들을 Grid로 배치 */}
            <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">

                <div className="lg:col-span-2">
                    {/* 매출 개요 차트 위젯 */}
                    <SalesOverview />
                </div>

                <div className="flex flex-col gap-6">
                    {/* 연간 요약 등 작은 위젯들 */}
                    <YearlyBreakup />
                </div>

            </div>
        </div>
    );
}