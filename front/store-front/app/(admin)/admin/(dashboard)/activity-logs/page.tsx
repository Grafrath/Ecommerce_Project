import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

export default function ActivityLogsPage() {
    return (
        <div className="space-y-6">
            <h2 className="text-3xl font-bold tracking-tight">관리자 활동 이력</h2>
            <div className="border rounded-md bg-white">
                {/* 추후 shadcn/ui add table 명령어를 통해 설치된 컴포넌트 사용 */}
                <Table>
                    <TableHeader>
                        <TableRow>
                            <TableHead>시간</TableHead>
                            <TableHead>관리자 ID</TableHead>
                            <TableHead>활동 내용</TableHead>
                            <TableHead>IP 주소</TableHead>
                        </TableRow>
                    </TableHeader>
                    <TableBody>
                        <TableRow>
                            <TableCell>2026-03-25 15:30</TableCell>
                            <TableCell>admin</TableCell>
                            <TableCell>상품 [로봇 장난감] 재고 수정 (+50)</TableCell>
                            <TableCell>192.168.1.5</TableCell>
                        </TableRow>
                        {/* 백엔드 API에서 불러온 데이터 매핑... */}
                    </TableBody>
                </Table>
            </div>
        </div>
    );
}