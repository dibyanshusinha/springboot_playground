import xml.etree.ElementTree as ET
import os
import re

def parse_jacoco(xml_path):
    if not os.path.exists(xml_path):
        return None
    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
        summary = {}
        for counter in root.findall("./counter"):
            c_type = counter.get("type")
            missed = int(counter.get("missed"))
            covered = int(counter.get("covered"))
            total = missed + covered
            pct = (covered / total * 100) if total > 0 else 100.0
            summary[c_type] = {
                "missed": missed,
                "covered": covered,
                "total": total,
                "pct": pct
            }
        return summary
    except Exception as e:
        print(f"Error parsing Jacoco report: {e}")
        return None

def parse_iut(html_path):
    if not os.path.exists(html_path):
        return None
    try:
        with open(html_path, "r", encoding="utf-8") as f:
            content = f.read()
        
        # Extract coverage percentage
        pct_match = re.search(r'<span class="[^"]*">(\d+)% route coverage</span>', content)
        pct = pct_match.group(1) if pct_match else "N/A"
        
        # Extract status
        status_match = re.search(r'<div class="overall ([^"]*)">([^<]*)</div>', content)
        status = status_match.group(2) if status_match else "N/A"
        
        # Extract metrics
        metrics = {}
        metric_matches = re.findall(r'<div class="metric"><span class="metric-label">([^<]*)</span><span class="metric-value [^"]*">([^<]*)</span></div>', content)
        for label, val in metric_matches:
            metrics[label.strip()] = val.strip()
            
        return {
            "pct": pct,
            "status": status,
            "metrics": metrics
        }
    except Exception as e:
        print(f"Error parsing IUT report: {e}")
        return None

def parse_e2e(html_path):
    if not os.path.exists(html_path):
        return None
    try:
        with open(html_path, "r", encoding="utf-8") as f:
            content = f.read()
        
        # Extract coverage percentage
        pct_match = re.search(r'<span class="[^"]*">(\d+)% route workflow coverage</span>', content)
        pct = pct_match.group(1) if pct_match else "N/A"
        
        # Extract status
        status_match = re.search(r'<div class="overall ([^"]*)">([^<]*)</div>', content)
        status = status_match.group(2) if status_match else "N/A"
        
        # Extract metrics
        metrics = {}
        metric_matches = re.findall(r'<div class="metric"><span class="metric-label">([^<]*)</span><span class="metric-value [^"]*">([^<]*)</span></div>', content)
        for label, val in metric_matches:
            metrics[label.strip()] = val.strip()
            
        return {
            "pct": pct,
            "status": status,
            "metrics": metrics
        }
    except Exception as e:
        print(f"Error parsing E2E report: {e}")
        return None

def generate_markdown():
    jacoco_path = "target/site/jacoco-unit/jacoco.xml"
    iut_path = "target/reports/api-scenario-coverage/index.html"
    e2e_path = "target/reports/e2e-scenario-report/index.html"
    
    jacoco = parse_jacoco(jacoco_path)
    iut = parse_iut(iut_path)
    e2e = parse_e2e(e2e_path)
    
    md = []
    md.append("## 🧪 PR Test & Coverage Report Summary\n")
    
    if jacoco:
        inst = jacoco.get("INSTRUCTION", {"pct": 0, "covered": 0, "total": 0})
        branch = jacoco.get("BRANCH", {"pct": 0, "covered": 0, "total": 0})
        md.append("### 📊 Unit Test Coverage (JaCoCo)")
        md.append(f"- **Instruction Coverage:** `{inst['pct']:.2f}%` ({inst['covered']}/{inst['total']})")
        md.append(f"- **Branch Coverage:** `{branch['pct']:.2f}%` ({branch['covered']}/{branch['total']})")
        md.append("")
        
    if iut:
        status_emoji = "✅" if iut['status'] == "PASSED" else "⚠️"
        md.append(f"### 🔗 Integration API Scenario Coverage (IUT) {status_emoji}")
        md.append(f"- **Status:** `{iut['status']}`")
        md.append(f"- **Route Coverage:** `{iut['pct']}%`")
        for k, v in iut['metrics'].items():
            md.append(f"- **{k}:** `{v}`")
        md.append("")
        
    if e2e:
        status_emoji = "✅" if e2e['status'] == "PASSED" else "⚠️"
        md.append(f"### 🚀 End-to-End Workflow Coverage (E2E) {status_emoji}")
        md.append(f"- **Status:** `{e2e['status']}`")
        md.append(f"- **Route Workflow Coverage:** `{e2e['pct']}%`")
        for k, v in e2e['metrics'].items():
            md.append(f"- **{k}:** `{v}`")
        md.append("")
    elif os.path.exists("target/e2e-triggered"):
        md.append("### 🚀 End-to-End Workflow Coverage (E2E) ⚠️")
        md.append("- **Status:** `FAILED` (check GitHub Actions workflow logs for details)")
        md.append("")
        
    return "\n".join(md)

if __name__ == "__main__":
    report_md = generate_markdown()
    print(report_md)
    os.makedirs("target", exist_ok=True)
    with open("target/pr_comment.md", "w", encoding="utf-8") as f:
        f.write(report_md)
