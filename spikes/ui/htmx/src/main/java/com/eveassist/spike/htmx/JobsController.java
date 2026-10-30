package com.eveassist.spike.htmx;

import com.eveassist.spike.plan.JobBoard;
import com.eveassist.spike.plan.JobBoard.MoveRefused;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The job board. A drop posts the job and the target column; the server applies the move (or refuses it) and returns
 * the whole board, which htmx swaps in. A refused move simply re-renders the job where it was, with a message.
 */
@Controller
public class JobsController {

    private final JobBoard board;

    public JobsController(JobBoard board) {
        this.board = board;
    }

    @GetMapping("/jobs")
    public String page(Model model) {
        model.addAttribute("board", board.view());
        return "jobs";
    }

    /** The board as it is, to undo what the drag did on screen. */
    @GetMapping("/jobs/board")
    public String board(Model model) {
        model.addAttribute("board", board.view());
        return "jobs :: board";
    }

    @PostMapping("/jobs/move")
    public String move(@RequestParam String job, @RequestParam(required = false) String pilot, Model model) {
        try {
            model.addAttribute("board", board.move(job, pilot));
        } catch (MoveRefused refused) {
            model.addAttribute("board", board.view(refused.getMessage()));
        }
        return "jobs :: board";
    }

    @PostMapping("/jobs/reset")
    public String reset(Model model) {
        board.reset();
        model.addAttribute("board", board.view());
        return "jobs :: board";
    }
}
