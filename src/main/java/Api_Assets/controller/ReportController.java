package Api_Assets.controller;


import Api_Assets.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report")
public class ReportController {

    @Autowired
    private ReportService reportService;

    @GetMapping("/weekly")
    public ResponseEntity<String> generateWeeklyReport() {
        String report = reportService.buildWeeklyAssetReport();
        return ResponseEntity.ok(report);
    }
}


