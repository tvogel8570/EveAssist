package com.eveassist.spike.react;

import com.eveassist.spike.plan.JobBoard;
import com.eveassist.spike.plan.JobBoard.BoardView;
import com.eveassist.spike.plan.JobBoard.MoveRefused;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON API for the job board. The React app moves the job on screen first and then calls {@code move}; a refused
 * move answers 409 with the reason, and the app puts the job back.
 */
@RestController
@RequestMapping("/api/jobs")
public class JobsApiController {

    public record Move(String job, String pilot) {
    }

    public record Refusal(String message) {
    }

    private final JobBoard board;

    public JobsApiController(JobBoard board) {
        this.board = board;
    }

    @GetMapping
    public BoardView board() {
        return board.view();
    }

    @PostMapping("/move")
    public BoardView move(@RequestBody Move move) {
        return board.move(move.job(), move.pilot());
    }

    @PostMapping("/reset")
    public BoardView reset() {
        board.reset();
        return board.view();
    }

    @ExceptionHandler(MoveRefused.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Refusal refused(MoveRefused refused) {
        return new Refusal(refused.getMessage());
    }
}
